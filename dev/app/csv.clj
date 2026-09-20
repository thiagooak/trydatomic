(ns app.csv
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.pprint]))

(defn pokemon->datoms [path]
  (with-open [reader (io/reader (io/resource path))]
    (doall (csv/read-csv reader))))

(comment
  (def data (pokemon->datoms "pokemon.csv"))
  (def header (first data))
  (def rows (rest data))

  header
  (first rows)
  (nth (first rows) 1)

  (map (fn [x] {:pokemon/number (nth x 0)
                :pokemon/name (nth x 1)
                :pokemon/type (vec (remove empty? [(nth x 2) (nth x 3)]))
                :stat/hp (Integer/parseInt (nth x 4))
                :stat/attack (Integer/parseInt (nth x 5))
                :stat/defense (Integer/parseInt (nth x 6))
                :stat/sp-attack (Integer/parseInt (nth x 7))
                :stat/sp-defense (Integer/parseInt (nth x 8))
                :stat/speed (Integer/parseInt (nth x 9))}) rows))
