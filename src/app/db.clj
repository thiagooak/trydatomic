(ns app.db
  (:require
   [clojure.java.io :as io]
   [datomic.client.api :as d]
   [clojure.edn :as edn]))

(def client (d/client {:server-type :datomic-local
                       :storage-dir :mem
                       :system "trydatomic"}))

(defn safe-dataset? [dataset]
  (let [allowed #{"friends" "pokemon"}]
    (contains? allowed dataset)))

(defn setup-db [conn dataset]
  (when-not (safe-dataset? dataset) (throw (Exception. "Unsafe Dataset")))
  (doseq [data (edn/read-string (slurp (io/resource (str dataset ".edn"))))]
    (d/transact conn {:tx-data data})))

(defn db-value
  "Get a database value for a specific dataset"
  [dataset]

  (let [db-name dataset]
    (d/delete-database client {db-name db-name})
    (d/create-database client {db-name db-name})
    (let [conn (d/connect client {db-name db-name})]
      (setup-db conn dataset)
      (d/db conn))))
