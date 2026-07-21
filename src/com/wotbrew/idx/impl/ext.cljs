(ns com.wotbrew.idx.impl.ext
  (:require [com.wotbrew.idx.impl.protocols :as p]
            [com.wotbrew.idx.impl.map :as imap]
            [com.wotbrew.idx.impl.vector :as ivec]
            [com.wotbrew.idx.impl.set :as iset]))

(extend-protocol
  p/Idx
  nil
  (-rewrap [coll auto] nil)
  (-get-eq [coll p] nil)
  (-get-uniq [coll p] nil)
  (-get-sort [coll p] nil)
  (-del-index [coll p kind] nil)
  (-add-index [coll p kind] (-> (p/-wrap coll false) (p/-add-index p kind)))
  (-elements [coll] nil)
  (-id-element-pairs [coll] nil)
  default
  (-rewrap [coll auto] coll)
  (-get-eq [coll p] nil)
  (-get-uniq [coll p] nil)
  (-get-sort [coll p] nil)
  (-del-index [coll p kind] coll)
  (-add-index [coll p kind] (-> (p/-wrap coll false) (p/-add-index p kind)))
  (-elements [coll] (if (map? coll) (vals coll) coll))
  (-id-element-pairs [coll]
    (cond
      (map? coll) (map (juxt key val) coll)
      (set? coll) (map (fn [x] [x x]) coll)
      :else (map-indexed vector coll))))

(extend-protocol p/Property
  default
  (-property [this element] (get element this))
  function
  (-property [this element] (this element))
  nil
  (-property [this element] (get element nil))
  Keyword
  (-property [this element] (this element))
  ;; Var, MetaFn and MultiFn are deftypes (typeof "object"), so the `function`
  ;; clause above never sees them, and extending the Fn *marker protocol* is a
  ;; no-op for types that declare Fn inline — each concrete invocable type must
  ;; be listed here explicitly or it falls to `default` and is silently treated
  ;; as a get-key (matching nothing). The JVM side dispatches on the Fn/Var
  ;; interfaces instead, which do cover these.
  Var
  (-property [this element] (this element))
  MetaFn
  (-property [this element] (this element))
  MultiFn
  (-property [this element] (this element)))

(defn- reject-sorted
  "Sorted maps/sets are refused rather than wrapped: a deftype cannot implement
  ISorted/IReversible conditionally, so a wrapper around a sorted collection
  would silently break subseq/rsubseq/rseq/sorted? while deceptively preserving
  seq order. Failing loudly at wrap time beats corrupting behavior at use time.
  (Mirrors the JVM ext.)"
  [coll]
  (throw (ex-info "idx cannot wrap sorted collections (the wrapper would not support subseq/rseq/sorted?); use a hash-based collection, or keep the sorted collection unindexed"
                  {:coll-type (type coll)})))

(extend-protocol p/Wrap
  nil
  (-wrap [this auto] (p/-wrap [] auto))
  default
  ;; cljs extend-protocol only takes concrete types, so unlike the JVM (which
  ;; extends the IPersistentMap/IPersistentSet interfaces) records and custom
  ;; map/set types cannot be enumerated above. Dispatch on map?/set? here so a
  ;; record wraps as an indexed map — matching JVM behavior — instead of being
  ;; torn into a vector of [k v] entries.
  (-wrap [this auto]
    (cond
      (map? this) (if (satisfies? ISorted this)
                    (reject-sorted this)
                    (imap/->IndexedPersistentMap this nil nil nil auto))
      (set? this) (if (satisfies? ISorted this)
                    (reject-sorted this)
                    (iset/->IndexedPersistentSet this nil nil nil auto))
      (vector? this) (ivec/->IndexedPersistentVector this nil nil nil auto)
      :else (p/-wrap (with-meta (vec this) (meta this)) auto)))

  PersistentArrayMap
  (-wrap [this auto] (imap/->IndexedPersistentMap this nil nil nil auto))
  PersistentHashMap
  (-wrap [this auto] (imap/->IndexedPersistentMap this nil nil nil auto))
  PersistentTreeMap
  (-wrap [this auto] (reject-sorted this))
  PersistentVector
  (-wrap [this auto] (ivec/->IndexedPersistentVector this nil nil nil auto))
  Subvec
  (-wrap [this auto] (ivec/->IndexedPersistentVector this nil nil nil auto))
  PersistentHashSet
  (-wrap [this auto] (iset/->IndexedPersistentSet this nil nil nil auto))
  PersistentTreeSet
  (-wrap [this auto] (reject-sorted this)))

(extend-protocol p/Unwrap
  nil
  (-unwrap [coll] coll)
  default
  (-unwrap [coll] coll))
