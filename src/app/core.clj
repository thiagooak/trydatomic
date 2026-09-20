(ns app.core
  (:require [org.httpkit.server :as http]
            [ring.middleware.params :refer [wrap-params]]
            [compojure.core :refer [defroutes GET POST]]
            [compojure.route :as route]
            [clojure.data.json :as json]
            [clojure.pprint :refer [pprint]]
            [clojure.edn :as edn]
            [datomic.client.api :as d]
            [app.db]
            [app.find-spec]
            [app.ui]
            [app.chapters])
  (:gen-class))

(defn find-fns [form]
  (cond
    (seq? form)
    (let [head (first form)
          called (when (symbol? head) #{head})]
      (into (or called #{})
            (mapcat find-fns form)))

    (coll? form)
    (into #{} (mapcat find-fns form))

    :else #{}))

(defn safe-q? [q]
  (let [allowed-fns #{'- '* '/ '+
                      '< '<= '= '> '>=
                      'count 'not 'not= 'not-join 'missing?
                      'or 'or-join 'and
                      'sum 'avg 'min 'max 'count-distinct 'distinct
                      'pull
                      'clojure.string/starts-with? 'clojure.string/includes?}]
    (every? allowed-fns (find-fns (edn/read-string q)))))

(defn run-q [dataset q]
  (when-not (safe-q? q) (throw (Exception. "Unsafe Query")))
  (let [db (app.db/db-value dataset)
        {:keys [query shape]} (app.find-spec/normalize (edn/read-string q))]
    (shape (d/q {:query query
                 :timeout 500
                 :args [db]}))))

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
            (if chapter (:content chapter) not-found-content))}))

(defroutes routes
  ;; In a real system, you would serve static files from a CDN
  (route/files "/" {:root "public"})
  ;; Api routes
  (POST "/api/q" req
    ;; do not rely on first and second
    (let [body (json/read-str (slurp (:body req)))
          in-name (first (keys body))
          in  (get-in body [in-name])
          out-name (second (keys body))
          dataset (get (:query-params req) "dataset")]

      {:status 200
       :headers {"Content-Type" "application/json"}
       :body (json/write-str {out-name
                              (try
                                (->> in
                                  (run-q dataset)
                                  (pprint)
                                  (with-out-str))
                                (catch Exception e (str e)))})}))

  (GET "/" _ (chapter-response "index"))

  (GET "/:chapter" [chapter] (chapter-response chapter)))

(defn run-server [port]
  (println (str "Server is listening on: http://localhost:" port))
  (http/run-server (wrap-params #'routes) {:port port}))

(defn -main [& args]
  (let [port (or (some-> (System/getenv "PORT") parse-long)
                 (some-> (first args) parse-long)
                 8080)]
    (run-server port)))

(comment
  (def stop-server (-main))
  (stop-server))
