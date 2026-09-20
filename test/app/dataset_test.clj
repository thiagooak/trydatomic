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

(deftest type-chart
  (testing "18 types and 51 / 61 / 8 matchups"
    (is (= 18 (ffirst (run '[:find (count ?t) :where [?t :type/name _]]))))
    (is (= 51 (count (run '[:find ?a ?b :where [?a :type/strong-against ?b]]))))
    (is (= 61 (count (run '[:find ?a ?b :where [?a :type/weak-against ?b]]))))
    (is (= 8 (count (run '[:find ?a ?b :where [?a :type/no-effect-on ?b]])))))
  (testing "a few known matchups"
    (is (= #{"Water" "Rock" "Ground"}
           (set (map first (run '[:find ?n :where [?f :type/name "Fire"] [?a :type/strong-against ?f] [?a :type/name ?n]])))))
    (is (= #{"Normal" "Fighting"}
           (set (map first (run '[:find ?n :where [?g :type/name "Ghost"] [?a :type/no-effect-on ?g] [?a :type/name ?n]]))))))
  (testing "every type a Pokemon has is a type entity, so joins by value never miss"
    (is (empty? (run '[:find ?n :where [_ :pokemon/type ?n] (not-join [?n] [_ :type/name ?n])]))))
  (testing "Dark is the only type without a Pokemon"
    (is (= [["Dark"]] (run '[:find ?n :where [?t :type/name ?n] (not-join [?n] [?p :pokemon/type ?n])]))))
  (testing "type entities are not Pokemon"
    (is (= 151 (ffirst (run '[:find (count ?e) :where [?e :pokemon/name _]]))))))

(defn- as-of
  "Runs `query` with `$then` bound to the database as of `generation`."
  [generation query]
  (run query (list 'as-of generation)))

(deftest generations-are-transactions
  (is (= [["Generation I" "1996-02-27"] ["Generation II" "1999-11-21"] ["Generation III" "2002-11-21"]
          ["Generation IV" "2006-09-28"] ["Generation V" "2010-09-18"] ["Generation VI" "2013-10-12"]
          ["Generation VII" "2016-11-18"]]
         (->> (run '[:find ?g ?released :where [?tx :tx/generation ?g] [?tx :tx/released ?released]])
              (map (fn [[g released]] [g (subs (str (.toInstant ^java.util.Date released)) 0 10)]))
              (sort-by second)))))

(deftest time-travel
  (testing "today's data is unchanged: Pikachu's defense was 30 until Generation VI"
    (is (= [[40]] (run '[:find ?d :where [?e :pokemon/name "Pikachu"] [?e :stat/defense ?d]])))
    (is (= [[30]] (as-of "Generation V" '[:find ?d :in $ $then :where [$then ?e :pokemon/name "Pikachu"] [$then ?e :stat/defense ?d]]))))
  (testing "seven Pokemon were retyped"
    (let [types-then (fn [generation]
                       (into {} (map (fn [[n ts]] [n (set ts)]))
                             (group-by first (as-of generation '[:find ?name ?type :in $ $then :where [$then ?e :pokemon/name ?name] [$then ?e :pokemon/type ?type]]))))
          then (update-vals (types-then "Generation I") #(set (map second %)))
          now (update-vals (group-by first (run '[:find ?name ?type :where [?e :pokemon/name ?name] [?e :pokemon/type ?type]])) #(set (map second %)))]
      (is (= #{"Clefairy" "Clefable" "Jigglypuff" "Wigglytuff" "Mr. Mime" "Magnemite" "Magneton"}
             (set (filter #(not= (then %) (now %)) (keys now)))))
      (is (= #{"Electric"} (then "Magnemite")))
      (is (= #{"Electric" "Steel"} (now "Magnemite")))
      (is (= #{"Normal"} (then "Clefairy")))))
  (testing "Magnemite gains Steel in Generation II, and Clefairy loses Normal in Generation VI"
    (is (= #{["Electric"] ["Steel"]}
           (set (as-of "Generation II" '[:find ?t :in $ $then :where [$then ?e :pokemon/name "Magnemite"] [$then ?e :pokemon/type ?t]]))))
    (is (= #{["Normal"]}
           (set (as-of "Generation V" '[:find ?t :in $ $then :where [$then ?e :pokemon/name "Clefairy"] [$then ?e :pokemon/type ?t]]))))
    (is (= #{["Fairy"]}
           (set (as-of "Generation VI" '[:find ?t :in $ $then :where [$then ?e :pokemon/name "Clefairy"] [$then ?e :pokemon/type ?t]])))))
  (testing "15, 17 and 18 types"
    (is (= [15 17 18 18]
           (mapv (fn [g] (ffirst (as-of g '[:find (count ?t) :in $ $then :where [$then ?t :type/name _]])))
                 ["Generation I" "Generation II" "Generation VI" "Generation VII"]))))
  (testing "Generation I had one Special stat, which is gone now"
    (is (= 151 (ffirst (as-of "Generation I" '[:find (count ?e) :in $ $then :where [$then ?e :stat/special _]]))))
    (is (= [[65]] (as-of "Generation I" '[:find ?s :in $ $then :where [$then ?e :pokemon/name "Bulbasaur"] [$then ?e :stat/special ?s]])))
    (is (empty? (run '[:find ?e :where [?e :stat/special _]]))))
  (testing "eight Pokemon gained attack after Generation V"
    (is (= #{"Arbok" "Beedrill" "Dugtrio" "Farfetch'd" "Golem" "Nidoking" "Nidoqueen" "Poliwrath"}
           (set (map first (as-of "Generation V" '[:find ?name ?old ?new :in $ $then :where [$ ?e :pokemon/name ?name] [$then ?e :stat/attack ?old] [$ ?e :stat/attack ?new] [(not= ?old ?new)]]))))))
  (testing "five gained a type and two lost one"
    (is (= 5 (count (as-of "Generation V" '[:find ?name ?type :in $ $then :where [$ ?e :pokemon/name ?name] [$ ?e :pokemon/type ?type] (not [$then ?e :pokemon/type ?type])]))))
    (is (= #{["Clefairy" "Normal"] ["Clefable" "Normal"]}
           (set (as-of "Generation V" '[:find ?name ?type :in $ $then :where [$then ?e :pokemon/name ?name] [$then ?e :pokemon/type ?type] (not [$ ?e :pokemon/type ?type])])))))
  (testing "Clefairy's type history"
    (is (= #{["Normal" true "Generation I"] ["Normal" false "Generation VI"] ["Fairy" true "Generation VI"]}
           (set (run '[:find ?type ?added ?generation :in $ $h :where [$ ?e :pokemon/name "Clefairy"] [$h ?e :pokemon/type ?type ?tx ?added] [$h ?tx :tx/generation ?generation]]
                     '(history))))))
  (testing "since"
    (is (= #{["Arbok" 95] ["Dugtrio" 100] ["Farfetch'd" 90]}
           (set (run '[:find ?name ?attack :in $ $since :where [$since ?e :stat/attack ?attack] [$ ?e :pokemon/name ?name]]
                     '(since "Generation VI")))))))
