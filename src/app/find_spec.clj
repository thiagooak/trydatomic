(ns app.find-spec
  "The Datomic Client API only supports relation finds. This namespace lets
  queries use the other find specs ([?x ...], [?a ?b], ?x .) by rewriting them
  to a relation find and reshaping the result the way the Peer API would.")

(defn- split-find
  "Splits a query vector into [before find-clause after]. The find clause is
  everything between :find and the next keyword (:with, :in, :where, ...)."
  [query]
  (let [[before from-find] (split-with #(not= :find %) query)
        [clause after] (split-with (complement keyword?) (rest from-find))]
    [before clause after]))

(defn normalize
  "Returns {:query q :shape f}. `q` is `query` with a relation :find, and `f`
  turns the client's result for `q` into the result the original find spec asks
  for. Queries that are not in a vector form, or that already use a relation
  find, come back unchanged with `identity` as the shape."
  [query]
  (let [unchanged {:query query :shape identity}]
    (if-not (vector? query)
      unchanged
      (let [[before clause after] (split-find query)
            single (when (= 1 (count clause)) (first clause))
            rewrite (fn [find-elements shape]
                      {:query (-> (vec before)
                                  (conj :find)
                                  (into find-elements)
                                  (into after))
                       :shape shape})]
        (cond
          ;; collection: [?x ...]
          (and (vector? single) (= '... (last single)))
          (rewrite (butlast single) #(mapv first %))

          ;; tuple: [?a ?b]
          (vector? single)
          (rewrite single first)

          ;; scalar: ?x .
          (= '. (last clause))
          (rewrite (butlast clause) ffirst)

          :else unchanged)))))
