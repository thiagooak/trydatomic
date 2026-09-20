(ns app.ui-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [app.chapters :as chapters]
            [app.ui :as ui])
  (:import (java.io PushbackReader StringReader)))

(defn- read-all [text]
  (let [reader (PushbackReader. (StringReader. text))]
    (vec (take-while #(not= ::eof %) (repeatedly #(edn/read {:eof ::eof} reader))))))

(deftest keywords-share-a-line-with-their-items
  (testing "the layout the reviewer asked for"
    (is (= "[:find ?e
 :in $ ?fname ?lname
 :where [?e :user/firstName ?fname]
        [?e :user/lastName ?lname]]
"
           (ui/format-query '[:find ?e :in $ ?fname ?lname :where [?e :user/firstName ?fname] [?e :user/lastName ?lname]]))))
  (testing "a short query"
    (is (= "[:find ?name
 :where [?e :pokemon/name ?name]]
"
           (ui/format-query '[:find ?name :where [?e :pokemon/name ?name]]))))
  (testing "a find spec and :with stay next to their keyword"
    (is (= "[:find (max ?speed) .
 :where [?e :stat/speed ?speed]]
"
           (ui/format-query '[:find (max ?speed) . :where [?e :stat/speed ?speed]])))
    (is (= "[:find (count ?type)
 :with ?e
 :where [?e :pokemon/type ?type]]
"
           (ui/format-query '[:find (count ?type) :with ?e :where [?e :pokemon/type ?type]]))))
  (testing "a long pull pattern wraps under its first argument"
    (is (= "[:find (pull ?e
             [:pokemon/name :stat/hp :stat/attack :stat/defense
              :stat/sp-attack :stat/sp-defense :stat/speed])
 :where [?e :stat/speed ?speed]
        [(> ?speed 100)]]
"
           (ui/format-query '[:find (pull ?e [:pokemon/name :stat/hp :stat/attack :stat/defense :stat/sp-attack :stat/sp-defense :stat/speed])
                              :where [?e :stat/speed ?speed] [(> ?speed 100)]]))))
  (testing "nested clauses hang from their name"
    (is (= "[:find ?name
 :where [?e :pokemon/name ?name]
        (or [?e :pokemon/type \"Fire\"]
            [?e :pokemon/type \"Water\"]
            [?e :pokemon/type \"Electric\"]
            [?e :pokemon/type \"Ice\"])]
"
           (ui/format-query '[:find ?name :where [?e :pokemon/name ?name]
                              (or [?e :pokemon/type "Fire"] [?e :pokemon/type "Water"] [?e :pokemon/type "Electric"] [?e :pokemon/type "Ice"])]))))
  (testing "things that are not queries are printed as usual"
    (is (= "{:a 1}\n" (ui/format-query {:a 1})))))

(deftest inputs-follow-the-query
  (is (= "[:find ?name
 :in $ ?type
 :where [?e :pokemon/name ?name]
        [?e :pokemon/type ?type]]

;; inputs, in the order of :in (after $)
\"Fire\"
[[\"Pikachu\" 30] [\"Charizard\" 70]]
(as-of \"Generation V\")
"
         (ui/editor-text '[:find ?name :in $ ?type :where [?e :pokemon/name ?name] [?e :pokemon/type ?type]]
                         "Fire" [["Pikachu" 30] ["Charizard" 70]] '(as-of "Generation V")))))

(defn- runnable-forms
  "The forms (query and inputs) of every runnable in every chapter file."
  []
  (for [f (.listFiles (io/file "resources/chapters"))
        :when (and (str/ends-with? (.getName f) ".edn") (not= "chapters.edn" (.getName f)))
        form (let [found (atom [])]
               (walk/postwalk (fn [x]
                                (when (and (vector? x) (= :ui/runnable (first x))) (swap! found conj (vec (drop 2 x))))
                                x)
                              (edn/read-string (slurp f)))
               @found)]
    form))

(deftest the-layout-never-changes-what-a-query-means
  (let [all (runnable-forms)]
    (is (> (count all) 50))
    (doseq [forms all]
      (is (= forms (read-all (apply ui/editor-text forms)))
          (pr-str (first forms))))))
