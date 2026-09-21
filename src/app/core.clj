(ns app.core
  (:require [org.httpkit.server :as http]
            [ring.middleware.params :refer [wrap-params]]
            [compojure.core :refer [defroutes GET POST]]
            [compojure.route :as route]
            [clojure.data.json :as json]
            [clojure.pprint :refer [pprint]]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [datomic.client.api :as d]
            [app.db]
            [app.find-spec]
            [app.ui]
            [app.chapters])
  (:import (java.io PushbackReader StringReader))
  (:gen-class))

(def max-query-length 2000)
(def max-forms "The query and up to three inputs." 4)
(def max-results 1000)

(def allowed-fns #{'- '* '/ '+
                   '< '<= '= '> '>=
                   'count 'not 'not= 'not-join 'missing?
                   'or 'or-join 'and
                   'sum 'avg 'min 'max 'count-distinct 'distinct
                   'pull
                   'get-else 'str 'subs
                   'clojure.string/starts-with? 'clojure.string/includes?
                   'clojure.string/upper-case 'clojure.string/lower-case})

(def query-syntax '#{_ $ ... . %})

;; Unqualified names that a query resolves as functions, besides clojure.core's.
(def datomic-builtins '#{get-else get-some ground tuple untuple q missing?})

(defn- logic-var? [sym]
  (and (nil? (namespace sym)) (str/starts-with? (name sym) "?")))

(defn- source-var?
  "$, or a named data source like $then, which a query can declare in :in."
  [sym]
  (and (nil? (namespace sym)) (boolean (re-matches #"\$[a-z][a-z0-9-]*" (name sym)))))

(defn- vetted-rule-name?
  "A rule name can't be the name of a function, because a query resolves an
  unqualified name in function position as clojure.core's or a Datomic built-in.
  A rule called slurp would otherwise get calls to slurp past the check."
  [sym]
  (and (nil? (namespace sym))
       (boolean (re-matches #"[a-z][a-z0-9-]*[?!]?" (name sym)))
       (nil? (ns-resolve 'clojure.core sym))
       (not (datomic-builtins sym))))

(defn- rule-names
  "The names defined by rules among the query's inputs: the head of every
  [(name ?x ...) clause ...] inside an input vector."
  [inputs]
  (for [input inputs
        :when (vector? input)
        rule input
        :when (and (vector? rule) (seq? (first rule)) (symbol? (ffirst rule)))]
    (ffirst rule)))

(defn unsafe-symbol
  "The first symbol in the query or its inputs that isn't allowed, or nil.
  Every symbol is checked, not only the ones that start a list, so a function
  named as an argument (for example pull's :xform) is caught too. A symbol is
  allowed when it is a logic variable, a data source, query syntax, an allowed
  function, or the name of a rule defined in the inputs (and that name passes
  vetting)."
  [query & inputs]
  (let [rules (rule-names inputs)
        rule-ok? (set (filter vetted-rule-name? rules))]
    (or (first (remove vetted-rule-name? rules))
        (->> (cons query inputs)
             (mapcat #(tree-seq coll? seq %))
             (filter symbol?)
             (remove #(or (allowed-fns %) (query-syntax %) (logic-var? %) (source-var? %) (rule-ok? %)))
             first))))

(defn safe-q?
  "True when nothing in the query or its inputs is unsafe, see `unsafe-symbol`."
  [query & inputs]
  (nil? (apply unsafe-symbol query inputs)))

(defn- user-error
  "An error whose message is meant for the person writing the query."
  [message]
  (ex-info message {::user-error true}))

(defn- read-forms
  "The query and its inputs: the EDN forms in `q`, in order. None when the text
  is empty or only comments."
  [q]
  (when-not (string? q) (throw (user-error "The query must be text")))
  (when (> (count q) max-query-length)
    (throw (user-error (str "The query is too long (the limit is " max-query-length " characters)"))))
  (let [forms (try
                (let [reader (PushbackReader. (StringReader. q))]
                  (vec (take (inc max-forms)
                             (take-while #(not= ::eof %)
                                         (repeatedly #(edn/read {:eof ::eof} reader))))))
                (catch RuntimeException e
                  (throw (user-error (str "Could not read the query: " (ex-message e))))))]
    (when (> (count forms) max-forms)
      (throw (user-error (str "Too many inputs (the limit is " (dec max-forms) ")"))))
    forms))

;; An input like (as-of "Generation V") stands for a view of the database, in the
;; same place a real call would pass (d/as-of db t). It's turned into a record
;; before the safety check, so as-of, since and history never become functions
;; a query may call.
(defrecord DbView [view generation])

(defn- db-view
  "A DbView for (as-of \"generation\"), (since \"generation\") or (history), nil
  for any other input."
  [form]
  (when (and (seq? form) ('#{as-of since history} (first form)))
    (let [[head & args] form]
      (if (if (= head 'history)
            (empty? args)
            (and (= 1 (count args)) (string? (first args))))
        (->DbView (keyword head) (first args))
        (throw (user-error (if (= head 'history)
                             "history takes no arguments: (history)"
                             (str head " takes the name of a generation: (" head " \"Generation V\")"))))))))

(defn- generation-tx [db generation]
  (or (ffirst (d/q '[:find ?tx :in $ ?g :where [?tx :tx/generation ?g]] db generation))
      (let [known (->> (d/q '[:find ?g ?released :where [?tx :tx/generation ?g] [?tx :tx/released ?released]] db)
                       (sort-by second)
                       (map first))]
        (throw (user-error (str "There is no generation called " (pr-str generation)
                                (when (seq known) (str ". Try one of: " (str/join ", " known)))))))))

(defn- resolve-input [db input]
  (if (instance? DbView input)
    (let [{:keys [view generation]} input]
      (case view
        :as-of (d/as-of db (generation-tx db generation))
        :since (d/since db (generation-tx db generation))
        :history (d/history db)))
    input))

(defn run-q
  "The result of running the query in `q`, or nil when `q` has nothing in it,
  like a REPL with nothing to evaluate."
  [dataset q]
  (let [[query & inputs :as forms] (read-forms q)
        inputs (mapv #(or (db-view %) %) inputs)]
    (when (seq forms)
      (when-let [sym (apply unsafe-symbol query (remove #(instance? DbView %) inputs))]
        (throw (user-error (str "Unsafe Query: " sym " is not allowed"))))
      (let [db (app.db/db-value dataset)
            {:keys [query shape]} (app.find-spec/normalize query)]
        (try
          ;; :timeout is not enforced by Datomic Local, and :limit only trims the result.
          (shape (d/q {:query query
                       :timeout 500
                       :limit max-results
                       :args (into [db] (map #(resolve-input db %) inputs))}))
          ;; A query that matches every row against every other row (clauses that
          ;; share no variable) can use more memory than we have. The query's
          ;; data is garbage by now, so the server carries on.
          (catch OutOfMemoryError _
            (throw (user-error "That query needs too much memory. Check that its clauses share variables, otherwise every row is combined with every other row."))))))))

(defn- error-message
  "Datomic's own error messages help people fix their query. Anything else is
  logged and hidden."
  [e]
  (let [data (ex-data e)]
    (if (or (::user-error data) (:cognitect.anomalies/category data))
      (ex-message e)
      (do (.printStackTrace ^Throwable e)
          "Something went wrong running the query"))))

(defn- read-body [req]
  ;; Catches Throwable because deeply nested JSON overflows the stack, which is an Error.
  (try
    (json/read-str (slurp (:body req)))
    (catch Throwable _ nil)))

(defn q-response [req]
  (let [{:strs [dataset in out]} (:query-params req)
        query (get (read-body req) in)]
    (if (and (app.db/dataset? dataset) in out (string? query))
      {:status 200
       :headers {"Content-Type" "application/json"}
       :body (json/write-str
              {out (try
                     ;; keep {:pokemon/name ..} instead of #:pokemon{:name ..}
                     (binding [*print-namespace-maps* false]
                       (with-out-str (pprint (run-q dataset query))))
                     (catch Exception e (error-message e)))})}
      {:status 400
       :headers {"Content-Type" "text/plain"}
       :body "Bad request"})))

(defn chapter-response [slug]
  (let [chapters (app.chapters/chapters)
        chapter (app.chapters/find-chapter chapters slug)]
    {:status (if chapter 200 404)
     :headers {"Content-Type" "text/html"}
     :body (app.ui/page
            (if chapter "Learn Datomic Datalog" "Page not found")
            (app.ui/nav chapters (when chapter slug))
            (if chapter
              (list (:content chapter)
                    (apply app.ui/pager (app.chapters/neighbours chapters slug)))
              (app.ui/not-found)))}))

(defroutes routes
  ;; In a real system, you would serve static files from a CDN
  (route/files "/" {:root "public"})
  ;; Api routes
  (POST "/api/q" req (q-response req))

  (GET "/" _ (chapter-response "index"))

  (GET "/:chapter" [chapter] (chapter-response chapter)))

(defn wrap-security-headers [handler]
  (fn [req]
    (some-> (handler req)
            (update :headers merge {"X-Content-Type-Options" "nosniff"
                                    "X-Frame-Options" "DENY"
                                    "Referrer-Policy" "strict-origin-when-cross-origin"}))))

(defn run-server [port]
  (println (str "Server is listening on: http://localhost:" port))
  (http/run-server (-> #'routes wrap-params wrap-security-headers)
                   {:port port}))

(defn -main [& args]
  (let [port (or (some-> (System/getenv "PORT") parse-long)
                 (some-> (first args) parse-long)
                 8080)]
    (run-server port)))

(comment
  (def stop-server (-main))
  (stop-server))
