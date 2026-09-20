(ns app.ui
  (:require [clojure.data.json :as json]
            [clojure.pprint :refer [pprint]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [hiccup2.core :as h]
            [hiccup.page :as p]))

(def version "Used for cache busting. build.clj creates the version.txt file"
  (if-let [resource (io/resource "version.txt")]
    (slurp resource)
    "dev"))

(defn chapter-href [{:keys [slug]}]
  (if (= slug "index") "/" (str "/" slug)))

(defn pager
  "Previous and next chapter buttons. Either chapter may be nil."
  [previous following]
  [:div {:class "pager"}
   (when previous
     [:a {:href (chapter-href previous) :rel "prev" :class "button"} "← Previous"])
   (when following
     [:a {:href (chapter-href following) :rel "next" :class "button next"} "Next →"])])

(defn- chapter-list [chapters current-slug]
  [:ul {:class "chapter-list"}
   (map-indexed
    (fn [i {:keys [slug nav-title] :as chapter}]
      [:li [:a (cond-> {:href (chapter-href chapter)}
                 (= slug current-slug) (assoc :aria-current "page"))
            [:span {:class "num"} i]
            nav-title]])
    chapters)])

(defn nav
  "The chapter list: a sticky sidebar on wide screens and a Chapters menu on
  narrow ones (main.css shows one and hides the other). `current-slug` marks the
  chapter being read, and may be nil."
  [chapters current-slug]
  (list
   [:nav {:class "chapters" :aria-label "Chapters"}
    [:p {:class "nav-label"} "Chapters"]
    (chapter-list chapters current-slug)]
   [:details {:class "chapters-menu"}
    [:summary {:class "button"}
     "Chapters"
     [:svg {:viewBox "0 0 12 12" :aria-hidden "true"}
      [:path {:d "M2 4l4 4 4-4" :fill "none" :stroke "currentColor" :stroke-width "2"}]]]
    (chapter-list chapters current-slug)]))

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
      [:div {:class "layout"} nav
      [:main children]]
      [:footer
       [:a {:class "source" :href "https://github.com/thiagooak/trydatomic" :target "_blank" :rel "noopener noreferrer"}
        [:svg {:viewBox "0 0 16 16" :aria-hidden "true"}
         [:path {:d "M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0016 8c0-4.42-3.58-8-8-8z"}]]
        "Contribute on GitHub"]
       [:p {:class "disclaimer"} "This website is not associated with Nubank or Datomic"]]
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

(def ^:private line-width 72)

(defn- text
  "`form` as text that fits in `width` columns where it can. Never uses the
  #:ns{} shorthand for maps, which is hard to read."
  [form width]
  (binding [*print-namespace-maps* false
            clojure.pprint/*print-right-margin* width]
    (str/trimr (with-out-str (pprint form)))))

(defn- flat [form] (text form 100000))

(defn- indented
  "`lines`, all but the first indented by `n` columns."
  [n lines]
  (let [pad (apply str (repeat n \space))]
    (cons (first lines) (map #(str pad %) (rest lines)))))

(defn- close [lines closer]
  (let [v (vec lines)]
    (conj (pop v) (str (peek v) closer))))

(defn- hang
  "The lines of `form` broken to fit `width` columns. A call keeps its first
  argument next to its name and lines the rest up under it, and a vector lines
  its items up after the bracket.

    (or [?e :pokemon/type \"Fire\"]
        [?e :pokemon/type \"Water\"])"
  [form width]
  (let [one (flat form)]
    (cond
      (<= (count one) width) [one]

      (and (seq? form) (symbol? (first form)) (seq (rest form)))
      (let [head (str "(" (first form) " ")
            args (map #(hang % (- width (count head))) (rest form))]
        (close (concat [(str head (ffirst args))]
                       (map #(str (apply str (repeat (count head) \space)) %) (rest (first args)))
                       (mapcat #(map (fn [l] (str (apply str (repeat (count head) \space)) l)) %) (rest args)))
               ")"))

      (and (vector? form) (every? (complement coll?) form))
      (let [tokens (map flat form)
            lines (reduce (fn [lines token]
                            (let [line (peek lines)]
                              (if (> (+ (count line) 1 (count token)) width)
                                (conj lines (str " " token))
                                (conj (pop lines) (str line " " token)))))
                          [(str "[" (first tokens))]
                          (rest tokens))]
        (close lines "]"))

      (and (vector? form) (seq form))
      (let [items (map #(hang % (dec width)) form)]
        (close (concat [(str "[" (ffirst items))]
                       (map #(str " " %) (rest (first items)))
                       (mapcat #(map (fn [l] (str " " l)) %) (rest items)))
               "]"))

      :else (str/split-lines (text form width)))))

(defn- stacked
  "`head`, then each of `items` on its own line, aligned under the first one."
  [head items]
  (let [indent (inc (count head))
        pad (apply str (repeat indent \space))
        lines (map #(hang % (- line-width indent)) items)]
    (concat [(str head " " (ffirst lines))]
            (map #(str pad %) (rest (first lines)))
            (mapcat #(map (fn [l] (str pad l)) %) (rest lines)))))

(defn- section
  "The lines for a query keyword and its items: on one line when they fit,
  except for :where, whose clauses are always one under another."
  [[head & items]]
  (let [head (str head)
        single (map flat items)
        inline (str/join " " (cons head single))]
    (cond
      (empty? items) [head]
      (and (not= head ":where")
           (not-any? #(str/includes? % "\n") single)
           (<= (count inline) (- line-width 2))) [inline]
      :else (stacked head items))))

(defn format-query
  "A query vector as text: every keyword with its items on one line, and the
  :where clauses one under another.

    [:find ?e
     :in $ ?first ?last
     :where [?e :user/first ?first]
            [?e :user/last ?last]]

  Anything that isn't a query vector is printed with pprint. Reading the text
  gives back exactly `query`."
  [query]
  (if-not (and (vector? query) (keyword? (first query)))
    (str (text query line-width) "\n")
    (let [lines (->> (partition-by keyword? query)
                     (partition-all 2)
                     (mapcat (fn [[[kw] items]] (section (cons kw items))))
                     vec)]
      (str "[" (str/join "\n " lines) "]\n"))))

(defn editor-text
  "The text in a runnable's editor: the query, then any inputs in the order of
  :in (after $). The server reads it back as the query followed by its inputs."
  [query & inputs]
  (str (format-query query)
       (when (seq inputs)
         (str "\n;; inputs, in the order of :in (after $)\n"
              (apply str (map #(str (str/join "\n" (hang % line-width)) "\n") inputs))))))

(defn runnable [dataset query & inputs]
  (let [random-name (swap! runnable-counter inc)
        input-name (str "in" random-name)
        output-name (str "out" random-name)
        input-text (apply editor-text query inputs)]

    [:div {(str "data-signals:" input-name) (json/write-str input-text :escape-slash false :escape-unicode false)
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
                                               ;; typing is what updates the input signal, so put the
                                               ;; original text back into it too, or Run would still send the edit
                                               "$" input-name " = el.dataset.initial;"
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
   ;; text is shown as it is written, anything else is laid out as data
   (code "clojure" (if (string? input) input (format-query input))))
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

(defn not-found
  "The page for a path that isn't a chapter. `slug` is whatever was asked for."
  []
  [:div
    [:h1 "404 Not Found"]
    [:p "Entity not found. It may have evolved into another URL."]
    [:img {:src "/missingno.png" :width 160 :height 144
          :alt "A Game Boy battle screen with the message: Wild MISSINGNO. appeared!"}]
    [:p [:a {:href "/" :class "button"} "Back to the start"]]])
