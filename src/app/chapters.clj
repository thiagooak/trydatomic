(ns app.chapters
  "Loads chapters from resources/chapters/*.edn.

  chapters.edn is the manifest: an ordered vector of slugs. Every other file is
  a map {:nav-title \"...\" :content <hiccup>}, named after its slug.

  Hiccup in :content may use [:ui/runnable ...], [:ui/code ...] and
  [:ui/try-tip ...]. Those are expanded by calling the matching app.ui function."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.walk :as walk]
            [app.ui :as ui]))

(def components
  "Whitelist of hiccup tags that call an app.ui function."
  {:ui/runnable #'ui/runnable
   :ui/code     #'ui/code
   :ui/try-tip  #'ui/try-tip})

(defn- component? [form]
  (and (vector? form)
       (keyword? (first form))
       (= "ui" (namespace (first form)))))

(defn expand
  "Replaces every [:ui/xxx & args] in `form` with the result of calling xxx."
  [slug form]
  (walk/postwalk
   (fn [form]
     (if (component? form)
       (if-let [component (components (first form))]
         (apply component (rest form))
         (throw (ex-info (str "Unknown component " (first form) " in chapter " slug)
                         {:slug slug :component (first form)})))
       form))
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

(defn find-chapter
  "`slug` comes from the URL, so it is only ever matched against the already
  loaded chapters, never used to build a resource path."
  [chapters slug]
  (first (filter #(= slug (:slug %)) chapters)))
