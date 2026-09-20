(ns app.content
  (:require [app.ui]))

(defn one []
  [:div
   [:h1 "Learn Datomic Datalog"]
   [:p "This interactive website will help you learn how to query a Datomic database using Datalog."]
   [:p "Datalog is a declarative, logic-based query language. Like SQL, you describe "
    [:em "what"] " you want rather than " [:em "how"] " to find it. Unlike SQL, Datalog works by "
    "matching " [:strong "patterns"] " against facts, making it a natural fit for Datomic's "
    "data model. By the end of this guide you will be able to filter, aggregate, "
    "and traverse relationships across a real database of all 151 original Pokémon."]
   [:p "Click the \"Run\" button below to run your first Datomic Datalog query."]
   (app.ui/runnable "pokemon"
                    '[:find ?name
                      :where
                      [?entity :pokemon/type "Grass"]
                      [?entity :pokemon/name ?name]])
   (app.ui/try-tip
     [:p "Edit the query above to return Pokémon of other types like Fire or Electric."])
   [:p [:a {:href "/querying" :class "button"} "Next Chapter →"]]])

(defn two []
  [:div
   [:h1 "Modeling Data"]
   [:p "To understand Datomic's data model, we will rewrite a simple SQL database using Datomic."]
   [:p "The database tracks our friends' favorite food and drink."]
   [:div {:style {:display "flex"}}

    [:div [:p "Friends Table"]

     [:table
      [:tr [:th "id"] [:th "first_name"] [:th "last_name"]]
      [:tr [:td "1"] [:td "Helena"] [:td "Almeida"]]
      [:tr [:td "2"] [:td "Alice"] [:td "Campos"]]
      [:tr [:td "3"] [:td "Laura"] [:td "Ferreira"]]
      [:tr [:td "4"] [:td "Miguel"] [:td "Melo"]]
      [:tr [:td "5"] [:td "Arthur"] [:td "Ramos"]]
      [:tr [:td "6"] [:td "Noah"] [:td "Silva"]]]]

    [:div [:p "Likes Table"]
     [:table
      [:tr [:th "id"] [:th "friend_id"] [:th "type"] [:th "object"]]
      [:tr [:td "10"] [:td "1"] [:td "food"] [:td "pizza"]]
      [:tr [:td "11"] [:td "2"] [:td "food"] [:td "sushi"]]
      [:tr [:td "12"] [:td "3"] [:td "food"] [:td "pizza"]]
      [:tr [:td "13"] [:td "4"] [:td "food"] [:td "pizza"]]
      [:tr [:td "14"] [:td "5"] [:td "food"] [:td "tacos"]]
      [:tr [:td "15"] [:td "6"] [:td "food"] [:td "curry"]]
      [:tr [:td "16"] [:td "1"] [:td "drink"] [:td "beer"]]
      [:tr [:td "17"] [:td "2"] [:td "drink"] [:td "wine"]]
      [:tr [:td "18"] [:td "3"] [:td "drink"] [:td "water"]]
      [:tr [:td "19"] [:td "4"] [:td "drink"] [:td "water"]]
      [:tr [:td "20"] [:td "5"] [:td "drink"] [:td "beer"]]
      [:tr [:td "21"] [:td "6"] [:td "drink"] [:td "beer"]]]]]


   [:p "To get the names of friends who like pizza, we can run a query like:"]


   (app.ui/code "sql" "SELECT f.first_name FROM friends f
 LEFT JOIN likes l ON f.id = l.friend_id
 WHERE l.type = \"food\"
 AND l.object = \"pizza\"

 -- Helena, Laura, Miguel")

   [:p "Let's rewrite this database using Datomic."]

   [:p "We can't use the two tables we had before. Datomic has a "
    [:a {:href "https://docs.datomic.com/whatis/data-model.html#universal"} "Universal Schema"]
    ". Think of it as one big table with five columns that stores " [:strong "everything"] " in our Database. For now, let's focus on three of the five columns."]

   [:ol
    [:li [:strong "Entity"] " identifies the \"thing\" we are referring to"]
    [:li [:strong "Attribute"] " associates an Attribute with the Entity"]
    [:li [:strong "Value"] " defines the Value of the Attribute associated with the Entity"]]

   [:p "We can model the information from the SQL database
   using these three columns from the Universal Schema,
   and 4 new Attributes."]

   (app.ui/code '[:person/first-name
                  :person/last-name
                  :likes/food
                  :likes/drink])

   [:p "With this structure, data for Helena and Noah could look like this:"]

   [:table
    [:tr [:td "Entity"] [:td "Attribute"] [:td "Value"]]
    [:tr [:td "1000"] [:td ":person/first-name"] [:td "Helena"]]
    [:tr [:td "1000"] [:td ":person/last-name"] [:td "Almeida"]]
    [:tr [:td "1000"] [:td ":likes/food"] [:td "pizza"]]
    [:tr [:td "1000"] [:td ":likes/drink"] [:td "beer"]]
    [:tr [:td ""] [:td ",,,"] [:td ""]]
    [:tr [:td "1006"] [:td ":person/first-name"] [:td "Noah"]]
    [:tr [:td "1006"] [:td ":person/last-name"] [:td "Silva"]]
    [:tr [:td "1006"] [:td ":likes/food"] [:td "curry"]]
    [:tr [:td "1006"] [:td ":likes/drink"] [:td "beer"]]]


   [:p "Let's implement it."]

   [:p "To install Attributes we need to define at least three things for each of them:"]

   [:ul
    [:li ":db/ident an identifier, like :person/first-name"]
    [:li ":db/valueType a type for the values of this attribute, like :db.type/string or "
     [:a {:href "https://docs.datomic.com/schema/schema-reference.html#db-valuetype"} "others"]]
    [:li ":db/cardinality whether this attribute accepts "
     [:a {:href "https://docs.datomic.com/schema/schema-reference.html#db-cardinality"} "one"]
     " or "
     [:a {:href "https://docs.datomic.com/schema/schema-reference.html#db-cardinality"} "many"]
     " values"]]

   [:p "Let's say that the people in our database can only like one food and one drink at the same time.
   Our attributes could be defined and installed like this:"]

   (app.ui/code '(defn install-attributes [conn]
                   @(d/transact conn [{:db/ident :person/first-name
                                       :db/valueType :db.type/string
                                       :db/cardinality :db.cardinality/one
                                       :db/doc "A person's name"
                                       :db/unique :db.unique/identity}

                                      {:db/ident :person/last-name
                                       :db/valueType :db.type/string
                                       :db/cardinality :db.cardinality/one
                                       :db/doc "A person's last name"
                                       :db/unique :db.unique/identity}

                                      {:db/ident :likes/drink
                                       :db/valueType :db.type/string
                                       :db/cardinality :db.cardinality/one
                                       :db/doc "A favorite drink"}

                                      {:db/ident :likes/food
                                       :db/valueType :db.type/string
                                       :db/cardinality :db.cardinality/one
                                       :db/doc "A favorite food"}])))

   [:p "Now that our attributes are installed, let's add our data."]

   (app.ui/code '(defn load-data [conn]
                   @(d/transact conn [{:person/first-name "Helena"
                                       :person/last-name "Almeida"
                                       :likes/food "pizza"
                                       :likes/drink "beer"}

                                      {:person/first-name "Alice"
                                       :person/last-name "Campos"
                                       :likes/food "sushi"
                                       :likes/drink "wine"}

                                      {:person/first-name "Laura"  :person/last-name "Ferreira" :likes/food "pizza" :likes/drink "water"}
                                      {:person/first-name "Miguel" :person/last-name "Melo"     :likes/food "pizza" :likes/drink "water"}
                                      {:person/first-name "Arthur" :person/last-name "Ramos"    :likes/food "tacos" :likes/drink "beer"}
                                      {:person/first-name "Noah"   :person/last-name "Silva"    :likes/food "curry" :likes/drink "beer"}])))

   [:p "Finally, we can query our Datomic database to find the names of your friends who like pizza. Click \"Run\" below."]

   (app.ui/runnable "friends"
                    '[:find ?n
                      :where [?e :likes/food "pizza"]
                      [?e :person/first-name ?n]])


   [:h2 "Practice"]

   [:p "Edit the query above to:"]
   [:ol
    [:li "Find the :person/first-name and :person/last-name of friends who like pizza"]
    [:li "Only show friends who like pizza and beer"]]

   [:h2 "Bonus Queries"]
   [:p "What everyone likes"]
   (app.ui/runnable "friends"
                    '[:find ?n ?food ?drink
                      :where
                      [?e :person/first-name ?n]
                      [?e :likes/food ?food]
                      [?e :likes/drink ?drink]])

   [:p "Everyone that has no favorite food"]
   (app.ui/runnable "friends"
                    '[:find ?fn ?ln
                      :where
                      [?e :person/first-name ?fn]
                      [?e :person/last-name ?ln]
                      [(missing? $ ?e :likes/food)]])

   [:h2 "Resources"]
   [:ul [:li [:a {:href "https://en.wikipedia.org/wiki/Entity%E2%80%93attribute%E2%80%93value_model"}
              "Entity-attribute-value model"]]
    [:li [:a {:href "https://docs.datomic.com/whatis/data-model.html#universal"} "Datomic Docs: Data Model"]]]
   [:p [:a {:href "/"} "Previous"] " | " [:a {:href "/querying"} "Next"]]])

(defn three []
  [:div
   [:h1 "Querying Basics"]
   [:p "Before we continue, let's take a closer look at the data model powering our Pokemon database."]
   [:p "Each Pokemon is stored as an " [:strong "Entity"] ". Here is what Bulbasaur looks like:"]
   (app.ui/code
    '{:pokemon/name    "Bulbasaur",
      :pokemon/number  "001",
      :pokemon/type    ["Grass" "Poison"],
      :stat/attack     49,
      :stat/defense    49,
      :stat/hp         45,
      :stat/sp-attack  65,
      :stat/sp-defense 65,
      :stat/speed      45})
   [:p "Each key is an " [:strong "Attribute"] " (:pokemon/name, :stat/speed, etc"
    ") and each value is the " [:strong "Value"] " for that attribute on this entity (Bulbasaur, 45, etc)."]
   [:p "In Datomic, every entity is assigned a unique " [:strong "entity ID"] ". "
    "Let's find Bulbasaur's:"]
   (app.ui/runnable "pokemon"
                    '[:find ?entity-id
                      :where [?entity-id :pokemon/name "Bulbasaur"]])
   [:p "Now that we have the concept of an entity ID, we can flip the query around and ask: "
    "what Attributes does this entity have? "
    "We bind the attribute position to a logic variable, then resolve its human-readable name via "
    [:code ":db/ident"] ":"]
   (app.ui/runnable "pokemon"
                    '[:find ?attribute
                      :where
                      [?entity-id :pokemon/name "Bulbasaur"]
                      [?entity-id ?attribute-id _]
                      [?attribute-id :db/ident ?attribute]])
   [:p "The underscore " [:code "_"] " in the third position is a wildcard, it matches any value but does not bind it to a variable."]
   [:p "Finally, we can pull all attribute-value pairs at once by binding the value position too:"]
   (app.ui/runnable "pokemon"
                    '[:find ?attribute ?value
                      :where
                      [?entity-id :pokemon/name "Bulbasaur"]
                      [?entity-id ?attribute-id ?value]
                      [?attribute-id :db/ident ?attribute]])
   [:p "This works because of Datomic's "
    [:a {:href "https://docs.datomic.com/whatis/data-model.html#universal"} "Universal Schema"]
    ". Every fact in the database is stored following this Entity-Attribute-Value structure. "
    "Attributes are themselves entities, which is why we can traverse from "
    [:code "?attribute-id"] " to " [:code ":db/ident"] " to get the keyword name."]
   [:p "Try changing \"Bulbasaur\" to another Pokemon name in the queries above."]
   [:p "Take a look at the "
    [:a {:href "https://github.com/thiagooak/learn-datalog/blob/main/resources/pokemon.edn"} "EDN file"]
    " defining all of the Pokemon in our database."]
   [:p "Run the last query with a dual-typed Pokemon like \"Charizard\" and observe "
    "how many attribute-value rows appear — one for each fact stored about that entity."]
   [:p [:a {:href "/" :class "button"} "← Previous Chapter"] " | " [:a {:href "/predicates"} "Next"]]])

(defn four []
  [:div
   [:h1 "Filtering with Predicates"]
   [:p "In the previous chapter we matched patterns by specifying exact values like "
    [:code "\"Bulbasaur\""] ". Predicates let us filter rows using arbitrary truthy expressions."]
   [:p "A predicate clause is a function call wrapped in an extra pair of brackets:"]
   (app.ui/code "[( > ?speed 100 )]")
   [:p "Every variable used in the predicate must already be bound by an earlier pattern clause. "
    "Let's find all Pokemon with a speed stat above 100:"]
   (app.ui/runnable "pokemon"
                    '[:find ?name ?speed
                      :where
                      [?e :pokemon/name ?name]
                      [?e :stat/speed ?speed]
                      [(> ?speed 100)]])
   [:p "We can compare two bound variables against each other. "
    "Here are the Pokemon whose defense is strictly higher than their attack:"]
   (app.ui/runnable "pokemon"
                    '[:find ?name ?attack ?defense
                      :where
                      [?e :pokemon/name ?name]
                      [?e :stat/attack ?attack]
                      [?e :stat/defense ?defense]
                      [(> ?defense ?attack)]])
   [:p "Predicates work on strings too, this site allows "
    [:code "clojure.string/starts-with?"] " and " [:code "clojure.string/includes?"]
    "; in a real Datomic app, any Clojure function is available. "
    "Here we find Pokemon whose name starts with \"S\":"]
   (app.ui/runnable "pokemon"
                    '[:find ?name
                      :where
                      [?e :pokemon/name ?name]
                      [(clojure.string/starts-with? ?name "S")]])
   [:p "Or find Pokemon whose name contains \"saur\":"]
   (app.ui/runnable "pokemon"
                    '[:find ?name
                      :where
                      [?e :pokemon/name ?name]
                      [(clojure.string/includes? ?name "saur")]])
   [:p [:strong "Try:"] " Combine two predicates to find Pokemon whose name starts with \"S\" "
    [:em "and"] " whose speed is above 100. "
    "Or find Pokemon whose attack " [:em "and"] " defense are both above 80."]
   [:p [:a {:href "/querying"} "Previous"] " | " [:a {:href "/negation"} "Next"]]])

(defn five []
  [:div
   [:h1 "Negation"]
   [:p "Sometimes you want to exclude results rather than include them. "
    "Datomic provides three tools for this: " [:code "not"] ", "
    [:code "not-join"] ", and " [:code "missing?"] "."]

   [:h2 "not"]
   [:p [:code "not"] " excludes any entity for which the inner pattern matches. "
    "Variables already bound in the outer query are automatically joined. "
    "Let's find Pokemon that are not Grass type:"]
   (app.ui/runnable "pokemon"
                    '[:find ?name
                      :where
                      [?e :pokemon/name ?name]
                      (not [?e :pokemon/type "Grass"])])
   [:p "Because " [:code ":pokemon/type"] " is cardinality/many, this excludes Pokemon that have "
    [:em "any"] " Grass entry — so Bulbasaur (Grass/Poison) is excluded. "
    "Try changing \"Grass\" to \"Fire\" or \"Water\"."]

   [:h2 "not-join"]
   [:p [:code "not-join"] " works like " [:code "not"] " but lets you introduce fresh variables "
    "inside the negation that are only used there. "
    "You declare explicitly which outer variables to join."]
   [:p "Here we find " [:strong "single-type Pokemon"] " — those for which there is no second, "
    "different type value:"]
   (app.ui/runnable "pokemon"
                    '[:find ?name ?type
                      :where
                      [?e :pokemon/name ?name]
                      [?e :pokemon/type ?type]
                      (not-join [?e ?type]
                        [?e :pokemon/type ?other]
                        [(not= ?type ?other)])])
   [:p "For each " [:code "?name"] "/" [:code "?type"] " pair, "
    [:code "not-join"] " checks whether a different type value " [:code "?other"] " exists on the same entity. "
    "If it does, the row is dropped — leaving only single-type Pokemon."]

   [:h2 "missing?"]
   [:p [:code "missing?"] " checks that an entity has no value for a given attribute. "
    "Its arguments are the database binding " [:code "$"] ", the entity variable, and the attribute keyword."]
   [:p "Our Pokemon database has an optional " [:code ":pokemon/category"] " attribute — "
    "only Articuno, Zapdos, Moltres, and Mewtwo have it set to " [:code "true"] ". "
    "Every other Pokemon is missing this attribute entirely. "
    "Let's find all non-legendary Pokemon:"]
   (app.ui/runnable "pokemon"
                    '[:find ?name
                      :where
                      [?e :pokemon/name ?name]
                      [(missing? $ ?e :pokemon/category)]])
   [:p "This returns all ~147 Pokemon that lack the " [:code ":pokemon/category"] " attribute. "
    "Try adding a stat like " [:code "[?e :stat/hp ?hp]"] " and including " [:code "?hp"]
    " in " [:code ":find"] " — " [:code "missing?"] " only filters; the rest of the query works normally."]
   [:p [:strong "Try:"] " Modify the " [:code "not"] " query at the top to exclude a different type, like \"Ice\" or \"Dragon\". "
    "Or flip the " [:code "missing?"] " demo to find " [:em "legendary"] " Pokemon by replacing "
    [:code "missing?"] " with a pattern clause " [:code "[?e :pokemon/category :legendary]"] "."]
   [:p [:a {:href "/predicates"} "Previous"] " | " [:a {:href "/or-clauses"} "Next"]]])

(defn six []
  [:div
   [:h1 "Or Clauses"]
   [:p "Pattern clauses in " [:code ":where"] " are implicitly joined with AND — every clause must match. "
    "To express OR logic, Datomic Datalog provides " [:code "or"] " and " [:code "or-join"] "."]

   [:h2 "or"]
   [:p [:code "or"] " succeeds when " [:em "at least one"] " of its branches matches. "
    "All branches must use the same set of variables. "
    "Let's find Pokemon that are Fire " [:em "or"] " Water type:"]
   (app.ui/runnable "pokemon"
                    '[:find ?name
                      :where
                      [?e :pokemon/name ?name]
                      (or [?e :pokemon/type "Fire"]
                          [?e :pokemon/type "Water"])])
   [:p "Try adding a third branch for \"Electric\"."]
   [:p "You can also use " [:code "or"] " to match against multiple values of the same attribute — "
    "here we find pure Fire-type or pure Water-type Pokemon by adding a second clause "
    "that rules out dual types:"]
   (app.ui/runnable "pokemon"
                    '[:find ?name ?type
                      :where
                      [?e :pokemon/name ?name]
                      [?e :pokemon/type ?type]
                      (or [?e :pokemon/type "Fire"]
                          [?e :pokemon/type "Water"])
                      (not-join [?e ?type]
                        [?e :pokemon/type ?other]
                        [(not= ?type ?other)])])

   [:h2 "or-join"]
   [:p [:code "or-join"] " works like " [:code "or"] " but you declare explicitly which outer variables "
    "each branch must bind. This is required when branches introduce " [:em "different"] " local variables."]
   [:p "Here we find Pokemon that are either Electric-type " [:em "or"] " have a speed above 100. "
    "The second branch introduces " [:code "?speed"] " which only exists inside that branch, "
    "so plain " [:code "or"] " would not work:"]
   (app.ui/runnable "pokemon"
                    '[:find ?name
                      :where
                      [?e :pokemon/name ?name]
                      (or-join [?e]
                        [?e :pokemon/type "Electric"]
                        (and [?e :stat/speed ?speed]
                             [(> ?speed 100)]))])
   [:p "The " [:code "[?e]"] " in " [:code "or-join"] " declares that " [:code "?e"] " is the only variable "
    "that must unify with the outer query. "
    [:code "?speed"] " is a local variable that lives only inside its branch."]
   [:p [:strong "Try:"] " Add a third " [:code "or"] " branch for \"Electric\" in the first query. "
    "Or modify the " [:code "or-join"] " query to find Pokemon that are Ground-type "
    [:em "or"] " have attack above 120."]
   [:p [:a {:href "/negation"} "Previous"] " | " [:a {:href "/cardinality-many"} "Next"]]])

(defn seven []
  [:div
   [:h1 "Cardinality Many"]
   [:p "Most attributes hold a single value per entity — " [:code ":db.cardinality/one"] ". "
    "But " [:code ":pokemon/type"] " is declared " [:code ":db.cardinality/many"] ", "
    "meaning an entity can have multiple values for the same attribute."]
   [:p "In the schema this looks like:"]
   (app.ui/code '{:db/ident       :pokemon/type
                  :db/valueType   :db.type/string
                  :db/cardinality :db.cardinality/many})
   [:p "The consequence for queries is important: "
    "binding a cardinality/many attribute produces " [:strong "one result row per value"] ". "
    "Let's see it in action — run this query and notice how Bulbasaur appears twice:"]
   (app.ui/runnable "pokemon"
                    '[:find ?name ?type
                      :where
                      [?e :pokemon/name ?name]
                      [?e :pokemon/type ?type]
                      (or [?e :pokemon/name "Bulbasaur"]
                          [?e :pokemon/name "Charmander"])])
   [:p "Bulbasaur has two type values (Grass and Poison) so it produces two rows. "
    "Charmander is pure Fire so it produces one."]

   [:h2 "Finding dual-type Pokemon"]
   [:p "We can use the multi-row behaviour to our advantage. "
    "By joining " [:code ":pokemon/type"] " to itself under two different variable names, "
    "we get all pairs of types on the same entity. "
    "Filtering with " [:code "not="] " keeps only the pairs where the two values differ — "
    "that is, entities that carry at least two distinct types:"]
   (app.ui/runnable "pokemon"
                    '[:find ?name
                      :where
                      [?e :pokemon/name ?name]
                      [?e :pokemon/type ?type1]
                      [?e :pokemon/type ?type2]
                      [(not= ?type1 ?type2)]])
   [:p "Because " [:code ":find"] " returns a " [:strong "set"] " of tuples, each name appears only once "
    "even though many " [:code "(?type1, ?type2)"] " combinations matched."]

   [:h2 "Counting values with aggregation"]
   [:p "We can count how many type values each Pokemon has by using the "
    [:code "count"] " aggregate in " [:code ":find"] ". "
    "This is a preview of the next chapter — for now just observe that "
    [:code "count"] " collapses all the type rows for one entity into a single number:"]
   (app.ui/runnable "pokemon"
                    '[:find ?name (count ?type)
                      :where
                      [?e :pokemon/name ?name]
                      [?e :pokemon/type ?type]])
   [:p "Pokemon with a count of 2 are dual-typed. Those with 1 are single-typed. "
    "You can sort by the count column in the results to spot the dual-types quickly."]
   [:p [:strong "Try:"] " Modify the dual-type query to also return " [:code "?type1"] " and "
    [:code "?type2"] " in " [:code ":find"] " to see which type combinations appear. "
    "Or change the " [:code "or"] " filter in the first query to show Bulbasaur and Pikachu side by side."]
   [:p [:a {:href "/or-clauses"} "Previous"] " | " [:a {:href "/aggregations"} "Next"]]])

(defn eight []
  [:div
   [:h1 "Aggregations"]
   [:p "Aggregation functions sit inside " [:code ":find"] " and collapse multiple rows into a single value. "
    "Any non-aggregated variable in " [:code ":find"] " becomes a grouping key — "
    "like SQL's " [:code "GROUP BY"] "."]

   [:h2 "count"]
   [:p "How many Pokemon belong to each type? "
    [:code "?type"] " is the grouping key; " [:code "(count ?name)"] " counts entries per group:"]
   (app.ui/runnable "pokemon"
                    '[:find ?type (count ?name)
                      :where
                      [?e :pokemon/name ?name]
                      [?e :pokemon/type ?type]])

   [:h2 "min and max"]
   [:p "What is the highest and lowest speed in the Pokedex?"]
   (app.ui/runnable "pokemon"
                    '[:find (min ?speed) (max ?speed)
                      :where
                      [?e :stat/speed ?speed]])
   [:p "Add " [:code "?name"] " as a grouping key to see each Pokemon's speed alongside the extremes "
    "within its group — or move on to the Find Specifications chapter where you'll learn "
    "how to return a single value as a scalar."]

   [:h2 "avg and sum"]
   [:p "Average attack across all Pokemon:"]
   (app.ui/runnable "pokemon"
                    '[:find (avg ?attack)
                      :where
                      [?e :stat/attack ?attack]])
   [:p "Sum of all HP values — a rough measure of total bulk across the Pokedex:"]
   (app.ui/runnable "pokemon"
                    '[:find (sum ?hp)
                      :where
                      [?e :stat/hp ?hp]])

   [:h2 "The :with clause"]
   [:p "There is a subtle trap with cardinality/many attributes. "
    "Before aggregation, Datomic builds a " [:strong "set"] " from the " [:code ":find"] " variables. "
    "Rows that look identical after projecting onto those variables are merged — "
    "which can silently drop data you intended to aggregate."]
   [:p "Compare these two queries. The first counts " [:em "distinct type strings"] " in the database:"]
   (app.ui/runnable "pokemon"
                    '[:find (count ?type)
                      :where
                      [?e :pokemon/type ?type]])
   [:p "The second uses " [:code ":with"] " to include " [:code "?e"] " in the pre-aggregation set, "
    "preventing type strings from collapsing across entities. "
    "It counts total " [:em "type assignments"] " across all Pokemon:"]
   (app.ui/runnable "pokemon"
                    '[:find (count ?type)
                      :with ?e
                      :where
                      [?e :pokemon/type ?type]])
   [:p "The first result is the number of unique type names (around 15). "
    "The second is the total number of type slots filled across all ~150 Pokemon "
    "(higher, because dual-typed Pokemon contribute two entries each)."]

   [:h2 "count-distinct"]
   [:p [:code "count-distinct"] " counts unique values of a variable within each group, "
    "ignoring duplicates. Its difference from " [:code "count"] " becomes visible when "
    [:code ":with"] " introduces duplicate rows. "
    "These two queries group by type and measure speed values in each group. "
    "With " [:code ":with ?e"] ", the same speed can appear multiple times (once per Pokemon with that type). "
    [:code "count"] " tallies every speed slot; " [:code "count-distinct"] " collapses repeated speeds:"]
   (app.ui/runnable "pokemon"
                    '[:find ?type (count ?speed)
                      :with ?e
                      :where
                      [?e :pokemon/type ?type]
                      [?e :stat/speed ?speed]])
   [:p "Now the same query using " [:code "count-distinct"] " — for types where multiple Pokemon "
    "share a speed value, the count will be lower:"]
   (app.ui/runnable "pokemon"
                    '[:find ?type (count-distinct ?speed)
                      :with ?e
                      :where
                      [?e :pokemon/type ?type]
                      [?e :stat/speed ?speed]])
   [:p [:strong "Try:"] " Add " [:code "?type"] " as a grouping key to the "
    [:code "avg"] " query to see average attack broken down by type. "
    "Or use " [:code "(min ?attack)"] " and " [:code "(max ?attack)"] " together to see the attack range per type."]
   [:p [:a {:href "/cardinality-many"} "Previous"] " | " [:a {:href "/find-specs"} "Next"]]])

(defn nine []
  [:div
   [:h1 "Find Specifications"]
   [:p "Every query so far has used the default " [:code ":find"] " form, "
    "which returns a " [:strong "set of tuples"] ". "
    "Datomic Datalog offers four distinct find specifications, each returning a different shape. "
    "Choosing the right one makes downstream code simpler."]

   [:h2 "Relation (default) — set of tuples"]
   [:p "The standard form. Each variable becomes a column; each matching combination becomes a row. "
    "Order within the set is not guaranteed."]
   (app.ui/runnable "pokemon"
                    '[:find ?name ?speed
                      :where
                      [?e :pokemon/name ?name]
                      [?e :stat/speed ?speed]
                      [(> ?speed 100)]])

   [:h2 "Collection — flat vector of one variable"]
   [:p "Wrap a single variable in " [:code "[?var ...]"] " to get a flat vector of its values "
    "instead of a set of single-element tuples. "
    "Useful when you only care about one column and want to pass it directly to other code:"]
   (app.ui/runnable "pokemon"
                    '[:find [?name ...]
                      :where
                      [?e :pokemon/name ?name]
                      [?e :stat/speed ?speed]
                      [(> ?speed 100)]])

   [:h2 "Scalar — single value"]
   [:p "Append " [:code "."] " after the variable to return exactly one value instead of a collection. "
    "When multiple rows match, which one you get is arbitrary — "
    "so scalars are most useful with aggregations that already produce a single row, "
    "like " [:code "(max ...)"] ":"]
   (app.ui/runnable "pokemon"
                    '[:find (max ?speed) .
                      :where
                      [?e :stat/speed ?speed]])
   [:p "Without the " [:code "."] " this would return " [:code "#{[110]}"] " — "
    "a set containing one single-element tuple. "
    "With it you get the bare number " [:code "110"] "."]

   [:h2 "Tuple — single row"]
   [:p "Wrap multiple variables in " [:code "[?a ?b]"] " (no " [:code "..."] ") "
    "to return exactly one tuple. "
    "Like the scalar form, it is most reliable when the query is guaranteed to match one row — "
    "for example, a lookup by a unique attribute:"]
   (app.ui/runnable "pokemon"
                    '[:find [?number ?type ?speed]
                      :where
                      [?e :pokemon/name "Pikachu"]
                      [?e :pokemon/number ?number]
                      [?e :pokemon/type ?type]
                      [?e :stat/speed ?speed]])
   [:p "Pikachu has exactly one entry in the database so this is safe. "
    "The result is a plain vector " [:code "[\"025\" \"Electric\" 90]"] " "
    "rather than a set of tuples — easy to destructure directly in Clojure."]
   [:p [:strong "Try:"] " Use the tuple find spec to look up a different Pokemon — try \"Mewtwo\" or \"Snorlax\". "
    "Or combine the scalar spec with " [:code "(min ?speed)"] " to return just the single lowest speed value in the Pokedex."]
   [:p [:a {:href "/aggregations"} "Previous"]]])

(defn ten []
  [:div
   [:h1 "Input Parameters"]
   [:p "Every query so far has had its filter values written directly into the query string. "
    "The " [:code ":in"] " clause lets you declare named input bindings so the same query "
    "can be called with different values at runtime — exactly like function parameters."]
   [:p "The database itself is always the first input, conventionally named " [:code "$"] ". "
    "Additional inputs follow:"]
   (app.ui/code "[:find ?name
 :in   $ ?type
 :where [?e :pokemon/name ?name]
        [?e :pokemon/type ?type]]")
   [:p "In Clojure you would call this query like:"]
   (app.ui/code "(d/q '[:find ?name
       :in   $ ?type
       :where [?e :pokemon/name ?name]
              [?e :pokemon/type ?type]]
     db \"Fire\")")
   [:p "The interactive demos on this site run queries with only the database as input, "
    "so the examples below show the equivalent queries with values inlined. "
    "The syntax and results are identical to what you would get with parameterised " [:code ":in"] " inputs."]

   [:h2 "Single scalar"]
   [:p "A bare variable in " [:code ":in"] " binds a single scalar value. "
    "This is the most common form — find all Pokemon of a given type:"]
   (app.ui/code ";; parameterised form
[:find ?name
 :in   $ ?type
 :where [?e :pokemon/name ?name]
        [?e :pokemon/type ?type]]
;; call: (d/q query db \"Fire\")")
   [:p "Equivalent runnable query with the value inlined:"]
   (app.ui/runnable "pokemon"
                    '[:find ?name
                      :where
                      [?e :pokemon/name ?name]
                      [?e :pokemon/type "Fire"]])

   [:h2 "Collection binding"]
   [:p "Wrap the variable in " [:code "[?var ...]"] " inside " [:code ":in"] " to accept "
    "a collection of values. The query matches any entity whose attribute value "
    "appears in the collection — like SQL's " [:code "IN (...)"] ":"]
   (app.ui/code ";; parameterised form
[:find ?name ?type
 :in   $ [?type ...]
 :where [?e :pokemon/name ?name]
        [?e :pokemon/type ?type]]
;; call: (d/q query db [\"Fire\" \"Water\" \"Electric\"])")
   [:p "Equivalent runnable query — note how the result is the union of all three types, "
    "which is exactly what collection binding achieves at the call site:"]
   (app.ui/runnable "pokemon"
                    '[:find ?name ?type
                      :where
                      [?e :pokemon/name ?name]
                      [?e :pokemon/type ?type]
                      (or [?e :pokemon/type "Fire"]
                          [?e :pokemon/type "Water"]
                          [?e :pokemon/type "Electric"])])

   [:h2 "Tuple binding"]
   [:p "Wrap multiple variables in " [:code "[?a ?b]"] " inside " [:code ":in"] " to destructure "
    "a single input tuple. Useful for passing a pair of related values like a range:"]
   (app.ui/code ";; parameterised form
[:find ?name ?speed
 :in   $ [?min-speed ?max-speed]
 :where [?e :pokemon/name ?name]
        [?e :stat/speed ?speed]
        [(>= ?speed ?min-speed)]
        [(<= ?speed ?max-speed)]]
;; call: (d/q query db [80 100])")
   [:p "Equivalent runnable query with the range values inlined:"]
   (app.ui/runnable "pokemon"
                    '[:find ?name ?speed
                      :where
                      [?e :pokemon/name ?name]
                      [?e :stat/speed ?speed]
                      [(>= ?speed 80)]
                      [(<= ?speed 100)]])

   [:h2 "Relation binding"]
   [:p "A relation binding accepts a collection of tuples — like a mini in-memory table. "
    "Each tuple is destructured into the declared variables:"]
   (app.ui/code ";; parameterised form — look up multiple Pokemon by name
[:find ?name ?hp
 :in   $ [[?name ?min-hp]]
 :where [?e :pokemon/name ?name]
        [?e :stat/hp ?hp]
        [(>= ?hp ?min-hp)]]
;; call: (d/q query db [[\"Pikachu\" 30] [\"Charizard\" 70]])")
   [:p "Each row in the input relation acts like a separate filter applied in parallel, "
    "then the results are unioned together."]
   [:p [:strong "Try:"] " Change the speed range in the tuple-binding example to 50–70 to see which Pokemon fall in that band. "
    "Or adapt the collection-binding equivalent to query for Grass, Fire, and Flying types all at once "
    "using three " [:code "or"] " branches."]
   [:p [:a {:href "/find-specs"} "Previous"] " | " [:a {:href "/pull"} "Next"]]])

(defn eleven []
  [:div
   [:h1 "Pull Expressions"]
   [:p "Every query so far has listed each desired attribute as a separate " [:code ":find"] " variable. "
    "Pull lets you retrieve a whole entity — or a chosen subset of its attributes — "
    "as a single map, without enumerating every binding explicitly."]
   [:p "The syntax is " [:code "(pull ?e pattern)"] " inside " [:code ":find"] ", "
    "where " [:code "pattern"] " is a vector of attribute keywords (or " [:code "[*]"] " for all of them)."]

   [:h2 "Selective pull"]
   [:p "List only the attributes you care about. "
    "Here we fetch name and speed for every Pokemon faster than 100, "
    "getting back one map per entity instead of a two-column tuple:"]
   (app.ui/runnable "pokemon"
                    '[:find (pull ?e [:pokemon/name :stat/speed])
                      :where
                      [?e :stat/speed ?speed]
                      [(> ?speed 100)]])
   [:p "Each result row contains a single map. "
    "This is especially useful when returning wide entities — "
    "compare pull with the equivalent multi-variable find:"]
   (app.ui/code ";;  multi-variable :find — verbose for many attributes
[:find ?name ?hp ?attack ?defense ?sp-attack ?sp-defense ?speed
 :where [?e :pokemon/name ?name]
        [?e :stat/hp ?hp]
        [?e :stat/attack ?attack]
        [?e :stat/defense ?defense]
        [?e :stat/sp-attack ?sp-attack]
        [?e :stat/sp-defense ?sp-defense]
        [?e :stat/speed ?speed]]

;; pull — concise, same result shape
[:find (pull ?e [:pokemon/name :stat/hp :stat/attack :stat/defense
                 :stat/sp-attack :stat/sp-defense :stat/speed])
 :where [?e :stat/speed ?speed]
        [(> ?speed 100)]]")

   [:h2 "Wildcard pull"]
   [:p "Use " [:code "[*]"] " to pull every attribute on the entity, "
    "including Datomic's own " [:code ":db/id"] ":"]
   (app.ui/runnable "pokemon"
                    '[:find (pull ?e [*])
                      :where
                      [?e :pokemon/name "Charizard"]])
   [:p "The wildcard is handy for exploration and debugging. "
    "In production code, prefer listing attributes explicitly "
    "so the shape of your data is clear and stable."]

   [:h2 "Pull with scalar find spec"]
   [:p "Combine pull with the scalar find spec (" [:code "."] ") "
    "to get a single entity map with no wrapping set or tuple:"]
   (app.ui/runnable "pokemon"
                    '[:find (pull ?e [:pokemon/name :pokemon/number
                                      :pokemon/type :stat/hp :stat/speed]) .
                      :where
                      [?e :pokemon/name "Pikachu"]])
   [:p "The result is a bare map — easy to pass directly to the rest of your application."]
   [:p [:strong "Try:"] " Add " [:code ":stat/attack"] " and " [:code ":stat/defense"] " to the selective pull pattern. "
    "Or use " [:code "[*]"] " on a different Pokemon like \"Snorlax\" to explore all of its stored attributes."]
   [:p [:a {:href "/input-params"} "Previous"] " | " [:a {:href "/rules"} "Next"]]])

(defn twelve []
  [:div
   [:h1 "Rules"]
   [:p "Rules let you name and reuse a group of " [:code ":where"] " clauses. "
    "Think of them as parameterised predicates — once defined, a rule can be invoked "
    "anywhere in a query just like a built-in clause. "
    "They keep complex queries readable and make shared logic easy to compose."]

   [:h2 "Defining and using a rule"]
   [:p "A rule is a vector of " [:em "rule heads"] ". "
    "Each head is itself a vector whose first element is the rule name and parameter list, "
    "followed by the body clauses:"]
   (app.ui/code "(def rules
  '[;; (strong? ?e) — true when attack AND speed are both above 80
    [(strong? ?e)
     [?e :stat/attack ?attack]
     [?e :stat/speed  ?speed]
     [(> ?attack 80)]
     [(> ?speed  80)]]])")
   [:p "Rules are passed to " [:code "d/q"] " via the special " [:code "%"] " input binding "
    "and invoked in " [:code ":where"] " like any other clause:"]
   (app.ui/code "(d/q '[:find ?name
       :in   $ %
       :where (strong? ?e)
              [?e :pokemon/name ?name]]
     db rules)")
   [:p "The equivalent inline query — without rules — produces the same result "
    "and can be run here:"]
   (app.ui/runnable "pokemon"
                    '[:find ?name
                      :where
                      [?e :pokemon/name ?name]
                      [?e :stat/attack ?attack]
                      [?e :stat/speed ?speed]
                      [(> ?attack 80)]
                      [(> ?speed 80)]])
   [:p "Rules become especially valuable when the same set of clauses is needed "
    "in multiple queries, or when you want to give a complex condition a meaningful name."]

   [:h2 "Multiple heads — rule-level OR"]
   [:p "A rule name can have more than one head. "
    "Datomic treats multiple heads as a " [:em "disjunction"] ": "
    "the rule succeeds if " [:em "any"] " head's body matches. "
    "This gives you named OR logic without repeating " [:code "or-join"] " everywhere:"]
   (app.ui/code "(def rules
  '[;; succeeds if the Pokemon is very fast OR hits very hard
    [(fast-or-strong? ?e)
     [?e :stat/speed ?speed]
     [(> ?speed 110)]]
    [(fast-or-strong? ?e)
     [?e :stat/attack ?attack]
     [(> ?attack 110)]]])")
   (app.ui/code "(d/q '[:find ?name
       :in   $ %
       :where (fast-or-strong? ?e)
              [?e :pokemon/name ?name]]
     db rules)")
   [:p "Equivalent runnable query using " [:code "or-join"] ":"]
   (app.ui/runnable "pokemon"
                    '[:find ?name
                      :where
                      [?e :pokemon/name ?name]
                      (or-join [?e]
                        (and [?e :stat/speed ?speed]   [(> ?speed 110)])
                        (and [?e :stat/attack ?attack] [(> ?attack 110)]))])

   [:h2 "Recursive rules"]
   [:p "A rule can invoke itself, giving you recursive logic that Datomic evaluates "
    "to a fixpoint — it keeps applying the rule until no new facts are derived. "
    "The canonical example is reachability in a graph."]
   [:p "Suppose you had an " [:code ":evolution/next"] " attribute linking each Pokemon "
    "to its evolution. A recursive rule could find all reachable evolutions from any starting point:"]
   (app.ui/code ";; (evolves-into? ?ancestor ?descendant)
(def evolution-rules
  '[;; base case: direct evolution
    [(evolves-into? ?a ?b)
     [?a :evolution/next ?b]]
    ;; recursive case: transitive evolution
    [(evolves-into? ?a ?c)
     [?a :evolution/next ?b]
     (evolves-into? ?b ?c)]])")
   (app.ui/code ";; Find everything Bulbasaur eventually evolves into
(d/q '[:find ?name
       :in   $ %
       :where [?bulbasaur :pokemon/name \"Bulbasaur\"]
              (evolves-into? ?bulbasaur ?descendant)
              [?descendant :pokemon/name ?name]]
     db evolution-rules)
;; => #{[\"Ivysaur\"] [\"Venusaur\"]}")
   [:p "The " [:code ":evolution/next"] " attribute is not in our Pokemon dataset, "
    "but the pattern applies to any directed graph stored in Datomic — "
    "org hierarchies, dependency trees, category taxonomies, and more."]
   [:p "One important constraint: every recursive rule must have a " [:em "base case"] " "
    "(a non-recursive head) that eventually terminates the recursion. "
    "Datomic stops when a full pass through all rule heads derives no new results."]
   [:p [:strong "Try:"] " Modify the " [:code "strong?"] " inline query to require both attack > 90 "
    [:em "and"] " speed > 90 — see how many Pokemon meet both criteria. "
    "Or change the " [:code "or-join"] " query to find Pokemon with attack > 130 "
    [:em "or"] " special attack > 130."]
   [:p [:a {:href "/pull"} "Previous"]]])

(def chapters
  (array-map
   nil                 {:nav-title "Index" :content one}
   ;;"modeling-data"     {:nav-title "Modeling data" :content two}
   "querying"          {:nav-title "Querying" :content three}
   "predicates"        {:nav-title "Predicates" :content four}
   "negation"          {:nav-title "Negation" :content five}
   "or-clauses"        {:nav-title "Or clauses" :content six}
   "cardinality-many"  {:nav-title "Cardinality many" :content seven}
   "aggregations"      {:nav-title "Aggregations" :content eight}
   "find-specs"        {:nav-title "Find specs" :content nine}
   ;;"input-params"      {:nav-title "Input params" :content ten}
   ;;"pull"              {:nav-title "Pull" :content eleven}
   ;;"rules"             {:nav-title "Rules" :content twelve}
   ))
