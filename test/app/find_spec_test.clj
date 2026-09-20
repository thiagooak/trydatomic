(ns app.find-spec-test
  (:require [clojure.test :refer [deftest is testing]]
            [app.find-spec :refer [normalize]]
            [app.core :refer [run-q]]))

(deftest normalize-find-specs
  (testing "collection"
    (let [{:keys [query shape]} (normalize '[:find [?name ...] :where [?e :pokemon/name ?name]])]
      (is (= '[:find ?name :where [?e :pokemon/name ?name]] query))
      (is (= ["a" "b"] (shape [["a"] ["b"]])))
      (is (= [] (shape [])))))

  (testing "scalar"
    (let [{:keys [query shape]} (normalize '[:find (max ?s) . :where [?e :stat/speed ?s]])]
      (is (= '[:find (max ?s) :where [?e :stat/speed ?s]] query))
      (is (= 150 (shape [[150]])))
      (is (nil? (shape [])))))

  (testing "scalar pull"
    (let [{:keys [query shape]} (normalize '[:find (pull ?e [*]) . :where [?e :pokemon/name "Pikachu"]])]
      (is (= '[:find (pull ?e [*]) :where [?e :pokemon/name "Pikachu"]] query))
      (is (= {:a 1} (shape [[{:a 1}]])))))

  (testing "tuple"
    (let [{:keys [query shape]} (normalize '[:find [?a ?b ?c] :where [?e :x ?a] [?e :y ?b] [?e :z ?c]])]
      (is (= '[:find ?a ?b ?c :where [?e :x ?a] [?e :y ?b] [?e :z ?c]] query))
      (is (= [1 2 3] (shape [[1 2 3]])))
      (is (nil? (shape [])))))

  (testing "the find clause ends at the next keyword"
    (is (= '[:find ?n :in $ ?t :where [?e :n ?n]]
           (:query (normalize '[:find [?n ...] :in $ ?t :where [?e :n ?n]]))))
    (is (= '[:find (count ?t) :with ?e :where [?e :t ?t]]
           (:query (normalize '[:find (count ?t) . :with ?e :where [?e :t ?t]])))))

  (testing "relation finds are left alone"
    (let [q '[:find ?name (count ?type) :where [?e :pokemon/name ?name] [?e :pokemon/type ?type]]
          {:keys [query shape]} (normalize q)]
      (is (= q query))
      (is (= [[1]] (shape [[1]])))))

  (testing "anything that is not a query vector is left alone"
    (is (= {:find '[?x]} (:query (normalize {:find '[?x]}))))))

(deftest find-specs-run-against-the-database
  (testing "collection"
    (let [result (run-q "pokemon" "[:find [?name ...] :where [?e :pokemon/name ?name] [?e :stat/speed ?speed] [(> ?speed 120)]]")]
      (is (vector? result))
      (is (every? string? result))
      (is (= #{"Mewtwo" "Electrode" "Aerodactyl" "Jolteon"} (set result)))))

  (testing "scalar"
    (is (= 150 (run-q "pokemon" "[:find (max ?speed) . :where [?e :stat/speed ?speed]]"))))

  (testing "tuple"
    (is (= ["025" "Electric" 90]
           (run-q "pokemon" "[:find [?number ?type ?speed] :where [?e :pokemon/name \"Pikachu\"] [?e :pokemon/number ?number] [?e :pokemon/type ?type] [?e :stat/speed ?speed]]"))))

  (testing "scalar pull"
    (is (= {:pokemon/name "Pikachu" :stat/speed 90}
           (run-q "pokemon" "[:find (pull ?e [:pokemon/name :stat/speed]) . :where [?e :pokemon/name \"Pikachu\"]]")))))
