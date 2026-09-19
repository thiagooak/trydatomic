(ns app.ui
  (:require [clojure.pprint :refer [pprint]]
            [clojure.java.io :as io]
            [hiccup2.core :as h]
            [hiccup.page :as p]))

(def version "Used for cache busting. build.clj creates the version.txt file"
  (if-let [resource (io/resource "version.txt")]
    (slurp resource)
    "dev"))

(defn nav-li [m]
  [:li [:a {:href (str "/" (key m))} (:nav-title (val m))]])

(defn nav [chapters]
  [:nav [:ul
         (map nav-li chapters)]])

(defn page [title nav children]
  (str
   (h/html
    {:mode :html}
    (p/doctype :html5)
    [:html {:lang  "en"}
     [:head
      [:title title]
      [:meta {:charset "UTF-8"}]
      [:meta {:name :viewport :content "width=device-width, initial-scale=1"}]
      [:meta {:description "This interactive website will help you learn how to query a Datomic databases using Datalog"}]
      [:link {:rel :preconnect :href "https://fonts.googleapis.com"}]
      [:link {:rel :preconnect :href "https://fonts.gstatic.com" :crossorigin true}]
      [:link {:rel :stylesheet :href "https://fonts.googleapis.com/css2?family=Inter:ital,opsz,wght@0,14..32,100..900;1,14..32,100..900&display=swap"}]
      [:link {:rel :stylesheet :href "https://cdn.jsdelivr.net/npm/@thiago.oak/code-highlighter@latest/prettylights.css"}]
      [:link {:rel :stylesheet :href (str "/main.css?v=" version)}]]
     [:body
       [:header {:style {:display "flex" :align-items "center" :justify-content "center" :margin-top "1em"}}
        [:img {:src "/eav.svg" :class "logo" :width 50 :height 50 :style {:margin-right "10px"}}]
        [:p {:style {:font-size "2em" :margin 0}} [:span {:style {:font-weight "bold"}} "try"] "datomic"]]
      [:div {:style {:display "flex"}} nav
      [:main children]]
      [:footer {:style {:text-align "center"}}
       "This website is not associated with Nubank or Datomic"
       [:br]
       [:a {:href "https://github.com/thiagooak/trydatomic"} "Source Code"]]
      [:script {:async true :src "https://www.googletagmanager.com/gtag/js?id=G-BMHSZQLLJ1"}]
      [:script (h/raw "window.dataLayer = window.dataLayer || [];
                function gtag () {dataLayer.push (arguments);}
                gtag ('js', new Date ());
                gtag ('config', 'G-BMHSZQLLJ1');")]
      [:script {:src "https://cdn.jsdelivr.net/npm/prismjs@1.30.0/components/prism-core.min.js" :data-manual "data-manual"}]
      [:script {:src "https://cdn.jsdelivr.net/npm/prismjs@1.30.0/components/prism-clojure.min.js"}]
      [:script {:src "https://cdn.jsdelivr.net/npm/prismjs@1.30.0/components/prism-sql.min.js"}]
      [:script {:type "module" :src "https://cdn.jsdelivr.net/npm/@thiago.oak/code-highlighter@latest/code-highlighter.js"}]
      [:script {:type "module" :src "https://cdn.jsdelivr.net/gh/starfederation/datastar@v1.0.3/bundles/datastar.js"}]]])))

(def runnable-counter (atom 0))

(defn runnable [dataset input]
  (let [random-name (swap! runnable-counter inc)
        input-name (str "in" random-name)
        output-name (str "out" random-name)
        input-text (with-out-str (pprint input))]

    [:div {(str "data-signals:" input-name) (str "'" input "'")
           (str "data-signals:" output-name) "',,,'"}
     [:code-highlighter {:id input-name
                         :language "clojure"
                         :contenteditable "plaintext-only"
                         :class "input"
                         :spellcheck "false"
                         :data-initial input-text
                         "data-on-signal-patch" "el.highlight()"
                         "data-on-signal-patch-filter" (str "{include: /^" input-name "$/}")
                         "data-on:input" (str "$" input-name " = el.innerText")}
      input-text]

     [:div
      [:button {"data-on:click__prevent" (str "@post('/api/q?dataset=" dataset "', {filterSignals: {include: /^" input-name "|" output-name "$/}})")
                :style {:margin "5px"}}
       "Run"]
      [:button {"data-on:click__prevent" (str "$" output-name " = ',,,';"
                                              "el = document.getElementById('" input-name "');"
                                              "el.textContent = el.dataset.initial;"
                                              "el.highlight()"
                                              )
                :style {:margin "5px"}}
       "Reset"]]

     [:code-highlighter {:language "clojure"
                         :name output-name
                         :class "output"
                         "data-on-signal-patch" "el.highlight()"
                         "data-on-signal-patch-filter" (str "{include: /^" output-name "$/}")
                         "data-text" (str "$" output-name)}]]))

(defn code
  ([input]
   (code "clojure" (with-out-str (pprint input))))
  ([lang input]
   [:code-highlighter {:language lang :class "input"} input]))
