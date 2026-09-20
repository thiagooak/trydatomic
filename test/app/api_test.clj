(ns app.api-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.data.json :as json]
            [clojure.edn :as edn]
            [app.core :as core :refer [run-q q-response safe-q?]]
            [app.ui :as ui])
  (:import (java.io ByteArrayInputStream)))

(defn- post
  "Calls the /api/q handler. `params` are the URL query params, `body` a JSON string."
  [params body]
  (q-response {:query-params params
               :body (ByteArrayInputStream. (.getBytes body "UTF-8"))}))

(defn- ask [q]
  (let [response (post {"dataset" "pokemon" "in" "in1" "out" "out1"}
                       (json/write-str {"in1" q}))]
    (-> response :body json/read-str (get "out1"))))

(deftest safe-q-checks-every-symbol
  (testing "functions named as arguments are rejected, not just called ones"
    (is (not (safe-q? (edn/read-string "[:find (pull ?e [(:pokemon/name :xform clojure.string/upper-case)]) :where [?e :pokemon/name _]]"))))
    (is (not (safe-q? (edn/read-string "[:find ?x :where [(identity System/exit) ?x]]"))))
    (is (not (safe-q? '[:find ?x :where [(read-string ?y) ?x]]))))
  (testing "logic variables, wildcards and query syntax are fine"
    (is (safe-q? '[:find [?name ...] :in $ ?t :where [?e :pokemon/name ?name] [(missing? $ ?e :x)] [?e _ ?t]]))
    (is (safe-q? '[:find (pull ?e [*]) . :where [?e :pokemon/name "Pikachu"]]))))

(deftest run-q-limits
  (testing "long queries are refused before they are parsed"
    (is (thrown-with-msg? Exception #"too long"
                          (run-q "pokemon" (str "[:find ?x :where " (apply str (repeat 3000 " ")) "]")))))
  (testing "deep nesting inside the length limit does not blow the stack"
    (is (some? (try (run-q "pokemon" (str (apply str (repeat 900 "[")) (apply str (repeat 900 "]"))))
                    (catch Exception e e)))))
  (testing "unreadable and unknown input"
    (is (thrown-with-msg? Exception #"Could not read the query" (run-q "pokemon" "[:find ?x :where")))
    (is (thrown-with-msg? Exception #"must be text" (run-q "pokemon" nil)))
    (is (thrown-with-msg? Exception #"Unsafe Dataset" (run-q "nope" "[:find ?n :where [?e :pokemon/name ?n]]")))))

(deftest results-are-capped
  (is (= core/max-results
         (count (run-q "pokemon" "[:find ?a ?b :where [?x :pokemon/name ?a] [?y :pokemon/name ?b]]")))))

(deftest concurrent-queries-do-not-interfere
  (let [pokemon "[:find (count ?e) . :where [?e :pokemon/name _]]"
        friends "[:find (count ?e) . :where [?e :person/first-name _]]"
        jobs (take 80 (cycle [["pokemon" pokemon] ["pokemon" pokemon] ["friends" friends] ["pokemon" pokemon]]))
        results (doall (pmap (fn [[dataset q]] [dataset (run-q dataset q)]) jobs))]
    (is (every? (fn [[dataset n]] (= n (if (= dataset "pokemon") 151 7))) results))))

(deftest api-endpoint
  (testing "answers with the query result under the requested output name"
    (is (= "150\n" (ask "[:find (max ?s) . :where [?e :stat/speed ?s]]"))))
  (testing "Datomic's message is passed on so people can fix their query"
    (is (re-find #"unbound" (ask "[:find ?nope :where [?e :pokemon/name ?n]]"))))
  (testing "rejections are explained"
    (is (= "Unsafe Query: System/exit is not allowed" (ask "[:find ?x :where [(System/exit 0) ?x]]"))))
  (testing "malformed requests get a 400"
    (is (= 400 (:status (post {"dataset" "pokemon" "in" "in1" "out" "out1"} "not json"))))
    (is (= 400 (:status (post {"dataset" "pokemon" "in" "in1"} "{}"))))
    (is (= 400 (:status (post {"dataset" "nope" "in" "in1" "out" "out1"} (json/write-str {"in1" "[]"})))))
    (is (= 400 (:status (post {"dataset" "pokemon" "in" "in1" "out" "out1"}
                              (json/write-str {"in1" 42})))))
    (is (= 400 (:status (post {"dataset" "pokemon" "in" "in1" "out" "out1"}
                              (apply str (concat (repeat 50000 "[") (repeat 50000 "]")))))))))

(defn- run
  "Runs a query and its inputs the way the editor's text posts them."
  [query & inputs]
  (run-q "pokemon" (apply ui/editor-text query inputs)))

(defn- names [result] (set (map first result)))

(deftest inputs-follow-the-query
  (testing "scalar"
    (is (= #{"Gastly" "Haunter" "Gengar"}
           (names (run '[:find ?name :in $ ?type :where [?e :pokemon/name ?name] [?e :pokemon/type ?type]] "Ghost")))))
  (testing "collection"
    (let [result (names (run '[:find ?name :in $ [?type ...] :where [?e :pokemon/name ?name] [?e :pokemon/type ?type]]
                             ["Ghost" "Dragon"]))]
      (is (contains? result "Gastly"))
      (is (contains? result "Dragonite"))
      (is (not (contains? result "Pikachu")))))
  (testing "tuple"
    (let [result (run '[:find ?name ?speed :in $ [?lo ?hi] :where [?e :pokemon/name ?name] [?e :stat/speed ?speed] [(>= ?speed ?lo)] [(<= ?speed ?hi)]]
                      [80 100])]
      (is (seq result))
      (is (every? (fn [[_ speed]] (<= 80 speed 100)) result))))
  (testing "relation"
    (is (= #{["Pikachu" 35] ["Charizard" 78]}
           (set (run '[:find ?name ?hp :in $ [[?name ?min-hp]] :where [?e :pokemon/name ?name] [?e :stat/hp ?hp] [(>= ?hp ?min-hp)]]
                     [["Pikachu" 30] ["Charizard" 70]])))))
  (testing "several inputs"
    (is (= #{"Charizard" "Rapidash" "Ninetales"}
           (set (map first (run '[:find ?name :in $ ?type ?min-speed :where [?e :pokemon/name ?name] [?e :pokemon/type ?type] [?e :stat/speed ?s] [(>= ?s ?min-speed)]]
                                "Fire" 100))))))
  (testing "a missing input is Datomic's own message"
    (is (thrown-with-msg? Exception #"expected 2 inputs"
                          (run '[:find ?name :in $ ?type :where [?e :pokemon/type ?type] [?e :pokemon/name ?name]]))))
  (testing "too many forms"
    (is (thrown-with-msg? Exception #"Too many inputs"
                          (run-q "pokemon" "[:find ?x :in $ ?a ?b ?c ?d :where [?x :pokemon/name _]] 1 2 3 4"))))
  (testing "empty text"
    (is (thrown-with-msg? Exception #"empty" (run-q "pokemon" "  ;; nothing here")))))

(def strong-rules '[[(strong? ?e) [?e :stat/attack ?a] [?e :stat/speed ?s] [(> ?a 80)] [(> ?s 80)]]])

(deftest rules-are-editable-inputs
  (testing "a rule set runs"
    (is (contains? (names (run '[:find ?name :in $ % :where (strong? ?e) [?e :pokemon/name ?name]] strong-rules))
                   "Charizard")))
  (testing "several heads, and recursion through :evolution/next when it exists"
    (is (contains? (names (run '[:find ?name :in $ % :where (fast-or-strong? ?e) [?e :pokemon/name ?name]]
                               '[[(fast-or-strong? ?e) [?e :stat/speed ?s] [(> ?s 110)]]
                                 [(fast-or-strong? ?e) [?e :stat/attack ?a] [(> ?a 110)]]]))
                   "Mewtwo")))
  (testing "rule names that would be resolved as functions are rejected"
    (doseq [rule-name '[println str slurp eval get-else q missing? tuple ground count clojure.core/slurp Strong strong_1]]
      (is (= rule-name (core/unsafe-symbol [:find '?y :in '$ '% :where (list rule-name '?x)]
                                           [[(list rule-name '?x) '[?x :pokemon/name _]]]))
          (str rule-name " must not be an allowed rule name"))))
  (testing "a rule called println cannot be used to call println"
    (is (thrown-with-msg? Exception #"println is not allowed"
                          (run '[:find ?y :in $ % :where [(println "x") ?y]]
                               '[[(println ?x) [?x :pokemon/name _]]]))))
  (testing "vetted names are accepted"
    (doseq [rule-name '[strong? evolves-into? fast-or-strong? same-type]]
      (is (safe-q? [:find '?e :in '$ '% :where (list rule-name '?e)]
                   [[(list rule-name '?e) '[?e :pokemon/name _]]])
          (str rule-name))))
  (testing "calling a rule that is not defined names the symbol"
    (is (thrown-with-msg? Exception #"Unsafe Query: strong\? is not allowed"
                          (run '[:find ?name :where (strong? ?e) [?e :pokemon/name ?name]])))))

(deftest results-do-not-use-namespace-map-shorthand
  (is (not (re-find #"#:" (ask "[:find (pull ?e [:pokemon/name]) :where [?e :pokemon/name \"Pikachu\"]]"))))
  (is (re-find #":pokemon/name \"Pikachu\"" (ask "[:find (pull ?e [:pokemon/name]) :where [?e :pokemon/name \"Pikachu\"]]"))))
