(ns app.chapters
  "Loads chapters from resources/chapters/*.edn.

  chapters.edn is the manifest: an ordered vector of slugs. Every other file is
  a map {:nav-title \"...\" :content <hiccup>}, named after its slug.

  Hiccup in :content may use [:ui/runnable ...], [:ui/code ...],
  [:ui/try-tip ...] and [:ui/value ...]. Those are expanded by calling the
  matching function."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [datomic.client.api :as d]
            [app.db :as db]
            [app.find-spec :as find-spec]
            [app.ui :as ui]))

(defn value
  "The result of a scalar query, like [:find (count ?e) . :where ...], run when
  the chapter is loaded. Use it for any number in the text that comes from the
  data, so the text can't fall out of date when the data changes."
  [dataset query]
  (let [{:keys [query shape]} (find-spec/normalize query)]
    (shape (d/q {:query query :args [(db/db-value dataset)]}))))

(def components
  "Whitelist of hiccup tags that call a function."
  {:ui/runnable #'ui/runnable
   :ui/code     #'ui/code
   :ui/try-tip  #'ui/try-tip
   :ui/value    #'value})

(defn- component? [form]
  (and (vector? form)
       (keyword? (first form))
       (= "ui" (namespace (first form)))))

(defn- external-link? [form]
  (and (vector? form)
       (= :a (first form))
       (map? (second form))
       (some-> (:href (second form)) (str/starts-with? "http"))))

(defn expand
  "Replaces every [:ui/xxx & args] in `form` with the result of calling xxx.
  Links to other sites open in a new tab, so nobody loses their place."
  [slug form]
  (walk/postwalk
   (fn [form]
     (cond
       (component? form)
       (if-let [component (components (first form))]
         (apply component (rest form))
         (throw (ex-info (str "Unknown component " (first form) " in chapter " slug)
                         {:slug slug :component (first form)})))

       (external-link? form)
       (update form 1 assoc :target "_blank" :rel "noopener noreferrer")

       :else form))
   form))

(defn- read-resource [path]
  (if-let [resource (io/resource path)]
    (edn/read-string (slurp resource))
    (throw (ex-info (str "Missing resource " path) {:path path}))))

(defn load-chapter [slug]
  (-> (read-resource (str "chapters/" slug ".edn"))
      (assoc :slug slug)
      (update :content #(expand slug %))))

(defn load-chapters
  "All published chapters, in manifest order."
  []
  (mapv load-chapter (read-resource "chapters/chapters.edn")))

(def chapters
  "Called as (chapters). Re-reads the files on every call in dev so edits show
  up on refresh, reads them once otherwise."
  (if (= ui/version "dev")
    load-chapters
    (memoize load-chapters)))

(defn neighbours
  "[previous next] for `slug` in the ordered `chapters`. Either is nil at the
  ends of the list, and both are nil for an unknown slug."
  [chapters slug]
  (let [i (.indexOf ^java.util.List (mapv :slug chapters) slug)]
    (when-not (neg? i)
      [(get chapters (dec i)) (get chapters (inc i))])))

(defn find-chapter
  "`slug` comes from the URL, so it is only ever matched against the already
  loaded chapters, never used to build a resource path."
  [chapters slug]
  (first (filter #(= slug (:slug %)) chapters)))
