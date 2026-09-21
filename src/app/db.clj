(ns app.db
  (:require
   [clojure.java.io :as io]
   [datomic.client.api :as d]
   [clojure.edn :as edn]))

(def client (d/client {:server-type :datomic-local
                       :storage-dir :mem
                       :system "trydatomic"}))

(defn- load-dataset
  "Creates an in-memory database from resources/<dataset>.edn and returns its db value."
  [dataset]
  (d/create-database client {:db-name dataset})
  (let [conn (d/connect client {:db-name dataset})]
    (doseq [data (edn/read-string (slurp (io/resource (str dataset ".edn"))))]
      (d/transact conn {:tx-data data}))
    (d/db conn)))

;; Each dataset is loaded once, on first use, and the immutable db value is
;; shared by every request. Queries are read-only, so this is safe. This map is
;; also the list of datasets a request may ask for.
(def ^:private db-values
  (into {} (map (fn [dataset] [dataset (delay (load-dataset dataset))]))
        ["pokemon"]))

(defn dataset? [dataset]
  (contains? db-values dataset))

(defn db-value
  "Get a database value for a specific dataset"
  [dataset]
  (if (dataset? dataset)
    @(db-values dataset)
    (throw (Exception. "Unsafe Dataset"))))
