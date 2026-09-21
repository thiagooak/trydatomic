# Learn Datalog

An interactive web app for learning [Datomic Datalog](https://docs.datomic.com/supported-ops.html#datalog).

The app serves a series of exercises. Each exercise includes an editable Datalog query that runs against a Datomic database populated with Pokemon data.

## Stack

- [http-kit](https://github.com/http-kit/http-kit) - HTTP server
- [Compojure](https://github.com/weavejester/compojure) - Routing
- [Hiccup](https://github.com/weavejester/hiccup) - HTML rendering from Clojure data structures
- [Datomic](https://docs.datomic.com/) - Database
- [Datastar](https://github.com/starfederation/datastar) - Frontend reactivity
- [Code Highlighter](https://github.com/thiagooak/code-highlighter) - Editable code blocks with syntax highlighting

## Development

Start the server:

```shell
clojure -X:run
```

Run tests:

```shell
clojure -X:test
```

Build an uberjar:

```shell
clojure -T:build uber
```

## Adding a chapter

Chapters are EDN files in `resources/chapters/`.

1. Create `resources/chapters/<slug>.edn`. The slug becomes the URL (`/<slug>`):

   ```clojure
   {:nav-title "My chapter"
    :content
    [:div
     [:h1 "My chapter"]
     [:p "Some text."]
     [:ui/runnable "pokemon" [:find ?name :where [?e :pokemon/name ?name]]]
     [:ui/try-tip [:p "Change the query above."]]]}
   ```

2. Add the slug to `resources/chapters/chapters.edn`. Its position sets the order in the nav. Files not listed there are not published.

`:content` is plain [Hiccup](https://github.com/weavejester/hiccup). Four extra tags call functions:

| Tag | Renders |
| --- | --- |
| `[:ui/runnable dataset query & inputs]` | An editable query with Run and Reset buttons. Any inputs after the query are passed to its `:in` clause in order, after `$`. A vector of rules is the input for `%`. `(as-of "Generation V")`, `(since "Generation V")` and `(history)` are views of the database, see the Time travel chapter. |
| `[:ui/code query]` | A read-only code block from data, pretty-printed. |
| `[:ui/code lang string]` | A read-only code block from a string, shown as written. |
| `[:ui/try-tip hiccup]` | A highlighted "TRY" box. |
| `[:ui/value dataset query]` | The result of a scalar query, like `[:find (count ?e) . :where [?e :pokemon/name _]]`, shown as text. Use it for any number in the text that comes from the data, so the text can't go stale when the data changes. |

In development the files are re-read on every request, so refreshing the browser shows your edits. `clojure -X:test` checks that every chapter loads and renders, and that every runnable query is allowed and executes.

## Deployment

```shell
fly deploy
```
