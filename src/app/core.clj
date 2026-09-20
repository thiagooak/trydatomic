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
  (:gen-class))

(def max-query-length 2000)
(def max-results 1000)

(def allowed-fns #{'- '* '/ '+
                   '< '<= '= '> '>=
                   'count 'not 'not= 'not-join 'missing?
                   'or 'or-join 'and
                   'sum 'avg 'min 'max 'count-distinct 'distinct
                   'pull
                   'clojure.string/starts-with? 'clojure.string/includes?})

(def query-syntax '#{_ $ ... .})

(defn- logic-var? [sym]
  (and (nil? (namespace sym)) (str/starts-with? (name sym) "?")))

(defn safe-q?
  "True when every symbol in `query` (already read as EDN) is a logic variable,
  query syntax or an allowed function. All symbols are checked, not only the
  ones that start a list, so a function named as an argument (for example
  pull's :xform) is rejected too."
  [query]
  (every? #(or (allowed-fns %) (query-syntax %) (logic-var? %))
          (filter symbol? (tree-seq coll? seq query))))

(defn- user-error
  "An error whose message is meant for the person writing the query."
  [message]
  (ex-info message {::user-error true}))

(defn- read-query [q]
  (when-not (string? q) (throw (user-error "The query must be text")))
  (when (> (count q) max-query-length)
    (throw (user-error (str "The query is too long (the limit is " max-query-length " characters)"))))
  (try
    (edn/read-string q)
    (catch RuntimeException e
      (throw (user-error (str "Could not read the query: " (ex-message e)))))))

(defn run-q [dataset q]
  (let [query (read-query q)]
    (when-not (safe-q? query) (throw (user-error "Unsafe Query")))
    (let [db (app.db/db-value dataset)
          {:keys [query shape]} (app.find-spec/normalize query)]
      (try
        ;; :timeout is not enforced by Datomic Local, and :limit only trims the result.
        (shape (d/q {:query query
                     :timeout 500
                     :limit max-results
                     :args [db]}))
        ;; A query that matches every row against every other row (clauses that
        ;; share no variable) can use more memory than we have. The query's
        ;; data is garbage by now, so the server carries on.
        (catch OutOfMemoryError _
          (throw (user-error "That query needs too much memory. Check that its clauses share variables, otherwise every row is combined with every other row.")))))))

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
                     (with-out-str (pprint (run-q dataset query)))
                     (catch Exception e (error-message e)))})}
      {:status 400
       :headers {"Content-Type" "text/plain"}
       :body "Bad request"})))

(def not-found-content
  [:div
   [:h1 "Page not found"]
   [:p [:a {:href "/"} "Back to the start"]]])

(defn chapter-response [slug]
  (let [chapters (app.chapters/chapters)
        chapter (app.chapters/find-chapter chapters slug)]
    {:status (if chapter 200 404)
     :headers {"Content-Type" "text/html"}
     :body (app.ui/page
            "Learn Datomic Datalog"
            (app.ui/nav chapters)
            (if chapter
              (list (:content chapter)
                    (apply app.ui/pager (app.chapters/neighbours chapters slug)))
              not-found-content))}))

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
