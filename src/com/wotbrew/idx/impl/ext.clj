(ns com.wotbrew.idx.impl.ext
  (:require [com.wotbrew.idx.impl.protocols :as p]
            [com.wotbrew.idx.impl.map :as imap]
            [com.wotbrew.idx.impl.vector :as ivec]
            [com.wotbrew.idx.impl.set :as iset])
  (:import (clojure.lang Fn Var Keyword MultiFn Sorted IPersistentMap IPersistentVector IPersistentSet)))

(extend-protocol p/Idx
  nil
  (-rewrap [coll auto] nil)
  (-get-eq [coll p] nil)
  (-get-uniq [coll p] nil)
  (-get-sort [coll p] nil)
  (-del-index [coll p kind] nil)
  (-add-index [coll p kind] (-> (p/-wrap coll false) (p/-add-index p kind)))
  (-elements [coll] nil)
  (-id-element-pairs [coll] nil)
  Object
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
  Fn
  (-property [this element] (this element))
  Var
  (-property [this element] (this element))
  Keyword
  (-property [this element] (this element))
  ;; MultiFn extends AFn but does NOT implement the Fn marker interface, so
  ;; without this clause multimethods would fall through to Object and be
  ;; looked up as keys — silently matching nothing.
  MultiFn
  (-property [this element] (this element))
  Object
  (-property [this element] (get element this))
  nil
  (-property [this element] (get element nil)))

(defn- reject-sorted
  "Sorted maps/sets are refused rather than wrapped: a deftype cannot implement
  Sorted/Reversible conditionally, so a wrapper around a sorted collection would
  silently break subseq/rsubseq/rseq/sorted? while deceptively preserving seq
  order. Failing loudly at wrap time beats corrupting behavior at use time."
  [coll]
  (throw (ex-info "idx cannot wrap sorted collections (the wrapper would not support subseq/rseq/sorted?); use a hash-based collection, or keep the sorted collection unindexed"
                  {:coll-type (type coll)})))

(extend-protocol p/Wrap
  IPersistentMap
  (-wrap [this auto]
    (if (instance? Sorted this)
      (reject-sorted this)
      (imap/->IndexedPersistentMap this nil nil nil auto)))
  IPersistentVector
  (-wrap [this auto] (ivec/->IndexedPersistentVector this nil nil nil auto))
  IPersistentSet
  (-wrap [this auto]
    (if (instance? Sorted this)
      (reject-sorted this)
      (iset/->IndexedPersistentSet this nil nil nil auto)))
  nil
  (-wrap [this auto] (p/-wrap [] auto))
  Object
  (-wrap [this auto] (p/-wrap (with-meta (vec this) (meta this)) auto)))

(extend-protocol p/Unwrap
  nil
  (-unwrap [coll] coll)
  Object
  (-unwrap [coll] coll))
