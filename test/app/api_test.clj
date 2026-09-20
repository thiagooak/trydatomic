(ns app.api-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.data.json :as json]
            [clojure.edn :as edn]
            [app.core :as core :refer [run-q q-response safe-q?]])
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
    (is (= "Unsafe Query" (ask "[:find ?x :where [(System/exit 0) ?x]]"))))
  (testing "malformed requests get a 400"
    (is (= 400 (:status (post {"dataset" "pokemon" "in" "in1" "out" "out1"} "not json"))))
    (is (= 400 (:status (post {"dataset" "pokemon" "in" "in1"} "{}"))))
    (is (= 400 (:status (post {"dataset" "nope" "in" "in1" "out" "out1"} (json/write-str {"in1" "[]"})))))
    (is (= 400 (:status (post {"dataset" "pokemon" "in" "in1" "out" "out1"}
                              (json/write-str {"in1" 42})))))
    (is (= 400 (:status (post {"dataset" "pokemon" "in" "in1" "out" "out1"}
                              (apply str (concat (repeat 50000 "[") (repeat 50000 "]")))))))))
