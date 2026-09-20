(ns app.chapters-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [hiccup2.core :as h]
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
        (is (string? (ui/page "t" (ui/nav [chapter] slug) (:content chapter))))))))

(deftest unknown-component-fails-loudly
  (is (thrown-with-msg? Exception #"Unknown component :ui/nope in chapter x"
                        (chapters/expand "x" [:div [:ui/nope "y"]]))))

(deftest find-chapter-only-matches-loaded-chapters
  (let [loaded (chapters/load-chapters)]
    (is (= "querying" (:slug (chapters/find-chapter loaded "querying"))))
    (is (nil? (chapters/find-chapter loaded "../../etc/passwd")))
    (is (nil? (chapters/find-chapter loaded "no-such-chapter")))))

(deftest neighbours-follow-the-manifest-order
  (let [loaded (chapters/load-chapters)
        slugs (mapv :slug loaded)]
    (is (= [nil (second slugs)] (map :slug (chapters/neighbours loaded (first slugs)))))
    (is (= [(nth slugs 1) (nth slugs 3)] (map :slug (chapters/neighbours loaded (nth slugs 2)))))
    (is (= [(last (butlast slugs)) nil] (let [[p n] (chapters/neighbours loaded (last slugs))] [(:slug p) (:slug n)])))
    (is (nil? (chapters/neighbours loaded "no-such-chapter")))))

(deftest pager-renders-buttons-for-the-neighbours-that-exist
  (let [html #(str (h/html {:mode :html} %))
        a {:slug "index" :nav-title "Index"}
        b {:slug "querying" :nav-title "Querying"}]
    (is (= "<div class=\"pager\"><a class=\"button next\" href=\"/querying\" rel=\"next\">Next →</a></div>"
           (html (ui/pager nil b))))
    (is (= "<div class=\"pager\"><a class=\"button\" href=\"/\" rel=\"prev\">← Previous</a></div>"
           (html (ui/pager a nil))))))

(deftest nav-marks-the-current-chapter
  (let [loaded (chapters/load-chapters)
        number (.indexOf ^java.util.List (mapv :slug loaded) "negation")
        html #(str (h/html {:mode :html} (ui/nav loaded %)))]
    (testing "one current chapter in the sidebar and one in the phone menu"
      (is (= 2 (count (re-seq #"aria-current" (html "negation")))))
      (is (re-find (re-pattern (str "<a aria-current=\"page\" href=\"/negation\"><span class=\"num\">"
                                    number "</span>Negation</a>"))
                   (html "negation"))))
    (testing "no current chapter, for example on the 404 page"
      (is (nil? (re-find #"aria-current" (html nil)))))
    (testing "chapters are numbered from 0 and Index links to /"
      (is (re-find #"<a href=\"/\"><span class=\"num\">0</span>Index</a>" (html "negation"))))))

(deftest chapters-do-not-hardcode-navigation-links
  (doseq [slug (all-slugs)
          :let [html (str (h/html {:mode :html} (:content (chapters/load-chapter slug))))]]
    (is (not (re-find #"<a [^>]*>(← )?(Previous|Next)( Chapter)?( →)?</a>" html)) slug)))

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
