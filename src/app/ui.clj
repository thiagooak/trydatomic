(ns app.ui
  (:require [clojure.pprint :refer [pprint]]
            [clojure.java.io :as io]
            [hiccup2.core :as h]
            [hiccup.page :as p]))

(def version "Used for cache busting. build.clj creates the version.txt file"
  (if-let [resource (io/resource "version.txt")]
    (slurp resource)
    "dev"))

(defn chapter-href [{:keys [slug]}]
  (if (= slug "index") "/" (str "/" slug)))

(defn nav-li [{:keys [nav-title] :as chapter}]
  [:li [:a {:href (chapter-href chapter) :style {:color "var(--ink)"}} nav-title]])

(defn pager
  "Previous and next chapter buttons. Either chapter may be nil."
  [previous following]
  [:div {:class "pager"}
   (when previous
     [:a {:href (chapter-href previous) :rel "prev" :class "button"} "← Previous"])
   (when following
     [:a {:href (chapter-href following) :rel "next" :class "button next"} "Next →"])])

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
      [:meta {:name "description" :content "This interactive website will help you learn how to query a Datomic databases using Datalog"}]
      [:link {:rel :preconnect :href "https://fonts.googleapis.com"}]
      [:link {:rel :preconnect :href "https://fonts.gstatic.com" :crossorigin true}]
      [:link {:rel :stylesheet :href "https://fonts.googleapis.com/css2?family=Inter:ital,opsz,wght@0,14..32,100..900;1,14..32,100..900&display=swap"}]
      [:link {:rel :stylesheet :href "https://cdn.jsdelivr.net/npm/@thiago.oak/code-highlighter@0.1.2/prettylights.css"
              :integrity "sha384-0XLRNB4k2BlxMZsKhXHKFPlhutMYjhLKqMHLFS6Nd/CZQuTSVV6lUkZDujDj7R1t" :crossorigin "anonymous"}]
      [:link {:rel :stylesheet :href (str "/main.css?v=" version)}]]
     [:body
       [:header
         [:a {:href "/" :style {:display "flex" :align-items "center" :justify-content "center" :margin-top "1em" :text-decoration "none" :color "var(--ink)"}}[:img {:src "/eav.svg" :class "logo" :width 50 :height 50 :style {:margin-right "10px"}}]
          [:p {:style {:font-size "2em" :margin 0}} [:span {:style {:font-weight "bold"}} "try"] "datomic"]]]
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
      [:script {:src "https://cdn.jsdelivr.net/npm/prismjs@1.30.0/components/prism-core.min.js" :data-manual "data-manual"
                :integrity "sha384-zLRFO4dwowZvh8kzutOb5AWhH7f39HeJp+N7PtHF1SQtTBnifRx0AtmvTYs3F4YV" :crossorigin "anonymous"}]
      [:script {:src "https://cdn.jsdelivr.net/npm/prismjs@1.30.0/components/prism-clojure.min.js"
                :integrity "sha384-j1owyG3mnp/ZB3LSee5CQBYKR+GPh01okdNEbUUbvK5y+U+So0sw+2z2zpdPeQVX" :crossorigin "anonymous"}]
      [:script {:src "https://cdn.jsdelivr.net/npm/prismjs@1.30.0/components/prism-sql.min.js"
                :integrity "sha384-/MKWdycCDliku23mP5sYXbZNuXrzgmQO/jsVxwPFn99dVOaXRyKsqDjarqpueGAp" :crossorigin "anonymous"}]
      [:script {:type "module" :src "https://cdn.jsdelivr.net/npm/@thiago.oak/code-highlighter@0.1.2/code-highlighter.js"
                :integrity "sha384-ZINuo7epnskJ88W6zqeL99sDcuODvwEJ7IN/K8juJMjXW0n1NQvZGzLDOvV1iaWE" :crossorigin "anonymous"}]
      [:script {:type "module" :src "https://cdn.jsdelivr.net/gh/starfederation/datastar@v1.0.3/bundles/datastar.js"
                :integrity "sha384-yHqFPXJio1slWODLf67ZFfuDhhPUccOzGpGJF0d5fitjEfihEVrVvzMsfH8TxCiO" :crossorigin "anonymous"}]]])))

(def runnable-counter (atom 0))

(defn runnable [dataset input]
  (let [random-name (swap! runnable-counter inc)
        input-name (str "in" random-name)
        output-name (str "out" random-name)
        input-text (with-out-str (pprint input))]

    [:div {(str "data-signals:" input-name) (str "'" input "'")
           (str "data-signals:" output-name) "',,,'"
           :style {:border "2px solid var(--ink)"
                   :margin "28px 0 36px"
                   :background "var(--paper)"}}
     [:header {:style {:display "flex"
       :align-items "center"
                       :gap "14px"
                       :background "var(--ink)"
                       :color "var(--paper)"
                       :padding "8px 14px"
                       :font-weight "700"
                       :font-size "1.0625rem"
                       :letter-spacing "-.01em"
                       }}
       [:span "Query"][:div {:style {:margin-left "auto"
         }}

       [:button {"data-on:click__prevent" (str "$" output-name " = ',,,';"
                                               "el = document.getElementById('" input-name "');"
                                               "el.textContent = el.dataset.initial;"
                                               "el.highlight()"
                                               )
                 :style {:margin "5px"}
                 :class "button"}
        "Reset"]

       [:button {"data-on:click__prevent" (str "@post('/api/q?dataset=" dataset "&in=" input-name "&out=" output-name "', {filterSignals: {include: /^" input-name "$/}})")
                 :style {:margin "5px"}
                 :class "button run"}
        [:svg {:viewBox "0 0 12 12" :aria-hidden "true"}
         [:path {:d "M1 0l11 6-11 6z"}]]
        "Run"]


       ]]
     [:code-highlighter {:id input-name
                         :language "clojure"
                         :contenteditable "plaintext-only"
                         :class "input"
                         :spellcheck "false"
                         :style {:border-bottom "2px solid var(--ink)"}
                         :data-initial input-text
                         "data-on-signal-patch" "el.highlight()"
                         "data-on-signal-patch-filter" (str "{include: /^" input-name "$/}")
                         "data-on:input" (str "$" input-name " = el.innerText")}
      input-text]

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

(defn try-tip
  [tip]
  [:div {:class "try"}
    [:span {:style {
      :font-size "0.75rem"
      :font-weight "700"
      :letter-spacing "0.1em"
      :text-transform "uppercase"
      :line-height "1.2"
    }} "TRY"]
    tip])
