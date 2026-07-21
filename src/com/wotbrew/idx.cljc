(ns com.wotbrew.idx
  "Provides secondary index support for clojure data structures."
  (:require [com.wotbrew.idx.impl.ext]
            [com.wotbrew.idx.impl.protocols :as p]
            [com.wotbrew.idx.impl.index :as i]))

(defn auto
  "Takes a set, vector or map and wraps it so that its elements support indexed queries.

  Indexes are created on demand to satisfy queries and then are reused.

  Indexes once realised will be maintained incrementally as you call conj, assoc and so on on the collection.

  Note: an ascending/descending query realises a sort index. Once one exists, all values of
  that property across the collection must remain mutually comparable — modifying the
  collection so that stops being true is undefined behaviour (currently it throws, like
  conj onto a sorted-set). nil property values are fine (they sort first).

  The coll must be a vector, map or set. If you pass a seq/seqable/iterable it is converted to a vector.
  Sorted maps/sets are not supported and will throw (the wrapper could not support subseq/rseq/sorted?).

  Metadata is carried over to the new structure.

  If the collection is already automatically indexed, it is returned as-is. If it
  has only manual indexes, those indexes are retained and automatic indexing is enabled."
  [coll]
  (p/-wrap coll true))

(defn unwrap
  "Returns the backing collection without indexes."
  [coll]
  (p/-unwrap coll))

(defn- predicate?
  "Is x a Predicate (a (match ...)/(pred ...) form) rather than plain data?

  On the JVM this deliberately tests the protocol's backing interface with
  `instance?` instead of `satisfies?`. This check sits on the hot path of every
  query with an almost-always-negative answer, and on Clojure 1.10 `satisfies?`
  caches no negative results — it reflectively walks the class hierarchy on
  every miss, costing microseconds where `instance?` costs nanoseconds.
  The trade-off: JVM Predicate implementations must implement the protocol
  inline (deftype/defrecord/reify); `extend`-based implementations will not be
  recognised. The protocol is documented as an implementation detail, so this
  is acceptable. CLJS `satisfies?` compiles to a cheap field check and has no
  such problem, hence the split."
  [x]
  #?(:clj  (instance? com.wotbrew.idx.impl.protocols.Predicate x)
     :cljs (satisfies? p/Predicate x)))

(defn- as-property
  "Predicates (such as those returned by match/pred) are indexed on their underlying property."
  [p]
  (if (predicate? p) (p/-prop p) p))

(defn index
  "Adds indexes to the collection, returning a new indexed collection.

  Specify a property/predicate to index, and a `kind` being:

  `:idx/unique` (for identify and replace-by calls)
  `:idx/hash` (for lookup calls)
  `:idx/sort` (for ascending/descending calls)

  All values of a property indexed with `:idx/sort` must be mutually comparable;
  modifying the collection so that stops being true (e.g. conj'ing an element whose
  property value is a string where the indexed values are ints) is undefined
  behaviour (currently it throws, like conj onto a sorted-set). nil property
  values are fine (they sort first).

  `:idx/unique` does not enforce uniqueness: adding an element whose indexed value
  duplicates another element's is silently accepted, and is undefined behaviour —
  the index keeps a single arbitrary (currently last-written) mapping, and a later
  update or removal of either duplicate can permanently drop the surviving
  element's entry until the index is rebuilt. Keeping the property unique is the
  caller's responsibility.

  The coll must be a vector, map or set. If you pass a seq/seqable/iterable it is converted to a vector.
  Sorted maps/sets are not supported and will throw (the wrapper could not support subseq/rseq/sorted?).

  Metadata is carried over to the new structure.

  Existing indexes are retained."
  ([coll] coll)
  ([coll p kind] (p/-add-index coll (as-property p) kind))
  ([coll p kind & more]
   (when (odd? (count more))
     (throw (ex-info "index requires property/kind pairs" {:unpaired-property (last more)})))
   (loop [coll (p/-add-index coll (as-property p) kind)
          more more]
     (if-some [[p kind & more] (seq more)]
       (recur (p/-add-index coll (as-property p) kind) more)
       coll))))

(defn delete-index
  "Deletes indexes from the collection, returning a new collection."
  ([coll p kind] (p/-del-index coll (as-property p) kind))
  ([coll p kind & more]
   (when (odd? (count more))
     (throw (ex-info "delete-index requires property/kind pairs" {:unpaired-property (last more)})))
   (loop [coll (p/-del-index coll (as-property p) kind)
          more more]
     (if-some [[p kind & more] (seq more)]
       (recur (p/-del-index coll (as-property p) kind) more)
       coll))))

(defrecord Comp [p1 p2]
  p/Property
  (-property [this element] (p/-property p1 (p/-property p2 element))))

(defn pcomp
  "Like clojure.core/comp but on properties."
  [p1 p2]
  (->Comp p1 p2))

(defrecord Select [ps]
  p/Property
  (-property [this element]
    (reduce (fn [m p] (assoc m p (p/-property p element))) {} ps)))

(defrecord Pred [p v]
  p/Predicate
  (-prop [this] p)
  (-predv [this] v))

(defn pred
  "Can be used in matches to nest truthy/falsy predicates.

  Use (pred p) or (pred p true) to test truthiness, and (pred p false) to test falsiness."
  ([p] (->Pred (pcomp boolean p) true))
  ([p v] (->Pred (pcomp boolean p) v)))

(defn- build-match-map
  [m p v]
  (if (predicate? v)
    (build-match-map m (pcomp (p/-prop v) p) (p/-predv v))
    (assoc m p v)))

(defn match
  "Takes interleaved property/value-or-predicate pairs and returns a predicate.

  You can then use it in short-form lookup/identify calls.

  (lookup coll (match :name \"Fred\", :age 42)))

  If each property value-or-predicate pair matches, the element
  is returned.

  This allows you to use composite indexes.

  When indexing manually, index a match by putting the placeholder :idx/value in each value position.

  e.g

  (index coll (match :foo :idx/value, :bar :idx/value) :idx/hash)"
  [p v & more]
  (when (odd? (count more))
    (throw (ex-info "match requires property/value pairs" {:unpaired-property (last more)})))
  (let [m (loop [m (build-match-map {} p v)
                 more more]
            (if-some [[prop val & tail] (seq more)]
              (recur (build-match-map m prop val) tail)
              m))]
    (if (= 1 (count m))
      ;; a single-property match shares its index with the plain property
      (let [[p v] (first m)]
        (->Pred p v))
      (->Pred (->Select (set (keys m))) m))))

(defrecord AsKey [k]
  p/Property
  (-property [this element] (get element k)))

(defn as-key
  "Returns a property that looks up k as a key. Only useful if you are using functions as keys."
  [k]
  (->AsKey k))

(defrecord Path2 [a b]
  p/Property
  (-property [this element] (->> element (p/-property a) (p/-property b))))

(defrecord Path3 [a b c]
  p/Property
  (-property [this element] (->> element (p/-property a) (p/-property b) (p/-property c))))

(defrecord Path [ps]
  p/Property
  (-property [this element]
    (reduce
      (fn [v p] (p/-property p v))
      element
      ps)))

(defn path
  "Returns a property that will drill down to some nested value by using the properties in the list. Think get-in."
  ([p] p)
  ([p1 p2] (->Path2 p1 p2))
  ([p1 p2 p3] (->Path3 p1 p2 p3))
  ([p1 p2 p3 p4 & more] (->Path (reduce conj [p1 p2 p3 p4] more))))

(defn lookup
  "Returns a seq of items where (p element) equals v.

  The returned sequence should be considered unsorted.

  The 2-ary takes a 'predicate' which composes a property with its expected value, either a `(match)` form, or a `(pred)` form."
  ([coll pred] (lookup coll (p/-prop pred) (p/-predv pred)))
  ([coll p v]
   (if (predicate? v)
     (lookup coll (pcomp (p/-prop v) p) (p/-predv v))
     (if-some [i (p/-get-eq coll p)]
       ;; (vals {}) is nil; return [] so a miss looks the same whether or not
       ;; the property happens to be indexed (the scan path returns a filterv)
       (let [m (i v {})] (or (vals m) []))
       (filterv (fn [element] (= v (p/-property p element))) (p/-elements coll))))))

(defn lookup-keys
  "Like lookup, but returns the indexes or keys of the matching elements."
  ([coll pred] (lookup-keys coll (p/-prop pred) (p/-predv pred)))
  ([coll p v]
   (if (predicate? v)
     (lookup-keys coll (pcomp (p/-prop v) p) (p/-predv v))
     (if-some [i (p/-get-eq coll p)]
       ;; (keys {}) is nil; return () so a miss looks the same whether or not
       ;; the property happens to be indexed (the scan path returns a seq)
       (let [m (i v {})] (or (keys m) ()))
       (map first (filter (fn [[_ element]] (= v (p/-property p element))) (p/-id-element-pairs coll)))))))

(defn identify
  "Returns the element where the property equals v.

  Behaviour is undefined if (p element) does not return a unique value across the collection."
  ([coll pred] (identify coll (p/-prop pred) (p/-predv pred)))
  ([coll p v]
   (if (predicate? v)
     (identify coll (pcomp (p/-prop v) p) (p/-predv v))
     (if-some [i (p/-get-uniq coll p)]
       ;; find (rather than get) so ids that are themselves nil (e.g. nil map keys) still resolve
       (when-some [kv (find i v)]
         (coll (val kv)))
       (reduce (fn [_ element] (when (= v (p/-property p element)) (reduced element))) nil (p/-elements coll))))))

(defn pk
  "Returns the key (index/map key) given a unique property/value pair or predicate.

  Returns nil when nothing matches. Note this is ambiguous when the matching
  element's key is itself nil (e.g. a nil map key) — use identify if you need
  to distinguish those cases."
  ([coll pred] (pk coll (p/-prop pred) (p/-predv pred)))
  ([coll p v]
   (if (predicate? v)
     (pk coll (pcomp (p/-prop v) p) (p/-predv v))
     (if-some [i (p/-get-uniq coll p)]
       (i v)
       (reduce (fn [_ [id element]] (when (= v (p/-property p element)) (reduced id))) nil (p/-id-element-pairs coll))))))

(defn replace-by
  "Replaces an element selected by an alternative key.

  e.g
  (replace-by customers :id 42 new-customer)

  Would return a new collection where the element identified by :id = 42 has been replaced by the value 'new-customer'.

  If no element matches, the collection is returned unchanged.

  Unlike the query functions, replace-by is only defined for vectors, maps and sets
  (the collections idx can wrap); calling it on other collections such as lists or
  lazy seqs is unsupported.

  Behaviour is undefined if (p element) does not return a unique value across the collection."
  ([coll pred element] (replace-by coll (p/-prop pred) (p/-predv pred) element))
  ([coll p v element]
   (if (predicate? v)
     (replace-by coll (pcomp (p/-prop v) p) (p/-predv v) element)
     (let [replace1 (fn [id]
                      (if (associative? coll)
                        (assoc coll id element)
                        (-> coll (disj id) (conj element))))]
       (if-some [i (p/-get-uniq coll p)]
         (if-some [kv (find i v)]
           (replace1 (val kv))
           coll)
         (let [[found? id]
               (reduce (fn [acc [id element]]
                         (if (= v (p/-property p element))
                           (reduced [true id])
                           acc))
                       [false nil]
                       (p/-id-element-pairs coll))]
           (if found? (replace1 id) coll)))))))

(defn ascending
  "Returns an ascending order seq of elements where (test (p element) v) returns true.

  The order is defined by the value of v.

  All values of (p element) across the collection must be mutually comparable.
  On an auto collection this query realises (and caches) a sort index, subjecting
  future modifications to the same comparability requirement (see `auto`).

  This is much like subseq in clojure.core."
  [coll p test v]
  (let [i (or (p/-get-sort coll p)
              (i/create-sorted-from-elements coll p))]
    (->> (subseq i test v)
         (mapcat (fn [e] (vals (val e)))))))

(defn descending
  "Returns a descending order seq of elements where (test (p element) v) returns true.

  The order is defined by the value of v.

  All values of (p element) across the collection must be mutually comparable.
  On an auto collection this query realises (and caches) a sort index, subjecting
  future modifications to the same comparability requirement (see `auto`).

  This is much like rsubseq in clojure.core."
  [coll p test v]
  (let [i (or (p/-get-sort coll p)
              (i/create-sorted-from-elements coll p))]
    (->> (rsubseq i test v)
         (mapcat (fn [e] (vals (val e)))))))
