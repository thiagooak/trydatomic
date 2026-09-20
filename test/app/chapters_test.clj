(ns app.chapters-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [app.chapters :as chapters]
            [app.core :as core]
            [app.ui :as ui]))

(defn- all-slugs
  "Every chapter file, including drafts that are not in the manifest."
  []
  (->> (.listFiles (io/file "resources/chapters"))
       (map #(.getName %))
       (filter #(str/ends-with? % ".edn"))
       (map #(str/replace % #"\.edn$" ""))
       (remove #{"chapters"})
       sort))

(deftest manifest-lists-existing-chapters
  (doseq [chapter (chapters/load-chapters)]
    (is (:nav-title chapter) (str (:slug chapter) " has a :nav-title"))
    (is (vector? (:content chapter)) (str (:slug chapter) " has hiccup :content"))))

(deftest every-chapter-file-loads-and-renders
  (doseq [slug (all-slugs)]
    (testing slug
      (let [chapter (chapters/load-chapter slug)]
        (is (:nav-title chapter))
        (is (string? (ui/page "t" (ui/nav [chapter]) (:content chapter))))))))

(deftest unknown-component-fails-loudly
  (is (thrown-with-msg? Exception #"Unknown component :ui/nope in chapter x"
                        (chapters/expand "x" [:div [:ui/nope "y"]]))))

(deftest find-chapter-only-matches-loaded-chapters
  (let [loaded (chapters/load-chapters)]
    (is (= "querying" (:slug (chapters/find-chapter loaded "querying"))))
    (is (nil? (chapters/find-chapter loaded "../../etc/passwd")))
    (is (nil? (chapters/find-chapter loaded "modeling-data")))))

(defn- runnables
  "[dataset query-string] for every runnable in an unexpanded chapter file."
  [slug]
  (let [raw (edn/read-string (slurp (io/resource (str "chapters/" slug ".edn"))))
        found (atom [])]
    (walk/postwalk (fn [form]
                     (when (and (vector? form) (= :ui/runnable (first form)))
                       (swap! found conj [(second form) (pr-str (nth form 2))]))
                     form)
                   raw)
    @found))

(deftest every-runnable-query-is-safe-and-runs
  (doseq [chapter (chapters/load-chapters)
          [dataset query] (runnables (:slug chapter))]
    (testing (str (:slug chapter) " " query)
      (is (core/safe-q? (edn/read-string query)))
      (is (some? (core/run-q dataset query))))))
