(ns app.dataset-test
  (:require [clojure.test :refer [deftest is testing]]
            [app.core :refer [run-q]]
            [app.ui :as ui]))

(defn- run [query & inputs]
  (run-q "pokemon" (apply ui/editor-text query inputs)))

(def evolves-into
  '[[(evolves-into? ?a ?b) [?a :evolution/next ?b]]
    [(evolves-into? ?a ?c) [?a :evolution/next ?b] (evolves-into? ?b ?c)]])

(deftest evolution-links
  (testing "72 links, and every evolved Pokemon comes from exactly one other"
    (let [links (run '[:find ?from ?to :where [?f :evolution/next ?t] [?f :pokemon/number ?from] [?t :pokemon/number ?to]])]
      (is (= 72 (count links)))
      (is (= 72 (count (set (map second links)))))))
  (testing "Eevee branches three ways"
    (is (= #{"Vaporeon" "Jolteon" "Flareon"}
           (set (map first (run '[:find ?name :where [?e :pokemon/name "Eevee"] [?e :evolution/next ?n] [?n :pokemon/name ?name]]))))))
  (testing "the numbers identify a Pokemon"
    (is (= 151 (ffirst (run '[:find (count ?n) :where [_ :pokemon/number ?n]])))))
  (testing "following the links from Bulbasaur"
    (is (= #{"Ivysaur" "Venusaur"}
           (set (map first (run '[:find ?name :in $ % :where [?b :pokemon/name "Bulbasaur"] (evolves-into? ?b ?d) [?d :pokemon/name ?name]]
                                evolves-into))))))
  (testing "and backwards"
    (is (= #{"Bulbasaur" "Ivysaur"}
           (set (map first (run '[:find ?name :in $ % :where [?v :pokemon/name "Venusaur"] (evolves-into? ?a ?v) [?a :pokemon/name ?name]]
                                evolves-into))))))
  (testing "no Pokemon evolves into itself, so the recursion ends"
    (is (empty? (run '[:find ?a :in $ % :where (evolves-into? ?a ?a)] evolves-into)))))
