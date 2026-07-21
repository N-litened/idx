(ns com.wotbrew.idx.impl.set
  (:require [com.wotbrew.idx.impl.protocols :as p]
            [com.wotbrew.idx.impl.index :as i]))

(deftype IndexedPersistentSet
  [s
   ^:mutable eq
   ^:mutable uniq
   ^:mutable sorted
   auto]
  p/Idx
  (-rewrap [idx a] (IndexedPersistentSet. s eq uniq sorted a))
  (-get-eq [idx p]
    (or (when (some? eq) (eq p))
        (when auto
          (let [i (i/create-eq-from-elements s p)
                neq (assoc eq p i)]
            (set! eq neq)
            i))))
  (-get-uniq [idx p]
    (or (when (some? uniq) (uniq p))
        (when auto
          (let [i (i/create-unique-from-elements s p)
                nuniq (assoc uniq p i)]
            (set! uniq nuniq)
            i))))
  (-get-sort [idx p]
    (or (when (some? sorted) (sorted p))
        (when auto
          (let [i (i/create-sorted-from-elements s p)
                nsorted (assoc sorted p i)]
            (set! sorted nsorted)
            i))))
  (-add-index [idx p kind]
    (case kind
      :idx/hash
      (if (get eq p)
        idx
        (IndexedPersistentSet. s (assoc eq p (i/create-eq-from-elements s p)) uniq sorted auto))
      :idx/unique
      (if (get uniq p)
        idx
        (IndexedPersistentSet. s eq (assoc uniq p (i/create-unique-from-elements s p)) sorted auto))
      :idx/sort
      (if (get sorted p)
        idx
        (IndexedPersistentSet. s eq uniq (assoc sorted p (i/create-sorted-from-elements s p)) auto))))
  (-del-index [idx p kind]
    (case kind
      :idx/hash
      (if (get eq p)
        (IndexedPersistentSet. s (not-empty (dissoc eq p)) uniq sorted auto)
        idx)
      :idx/unique
      (if (get uniq p)
        (IndexedPersistentSet. s eq (not-empty (dissoc uniq p)) sorted auto)
        idx)
      :idx/sort
      (if (get sorted p)
        (IndexedPersistentSet. s eq uniq (not-empty (dissoc sorted p)) auto)
        idx)))
  (-elements [idx] s)
  (-id-element-pairs [idx] (map (fn [x] [x x]) s))
  p/Wrap
  (-wrap [this a]
    (if (= a auto) this (p/-rewrap this a)))
  p/Unwrap
  (-unwrap [this] s)
  Object
  (toString [coll]
    (str s))
  (equiv [this other]
    (-equiv this other))

  ;; EXPERIMENTAL: subject to change
  (keys [coll]
    (es6-iterator (seq coll)))
  (entries [coll]
    (es6-set-entries-iterator (seq coll)))
  (values [coll]
    (es6-iterator (seq coll)))
  (has [coll k]
    (contains? coll k))
  (forEach [coll f]
    (doseq [[k v] coll]
      (f v k)))

  IPrintWithWriter
  (-pr-writer [coll writer opts] (-pr-writer s writer opts))

  ICloneable
  (-clone [_] (IndexedPersistentSet. s eq uniq sorted auto))

  IIterable
  (-iterator [coll] (-iterator s))

  IWithMeta
  (-with-meta [coll new-meta]
    (if (identical? new-meta (meta s))
      coll
      (IndexedPersistentSet. (with-meta s new-meta) eq uniq sorted auto)))

  IMeta
  (-meta [coll] (meta s))

  ICollection
  (-conj [coll o]
    ;; cljs set -conj allocates a new set even when o is already a member, so
    ;; the (identical? ns s) guard the JVM impl uses never fires here — guard on
    ;; membership instead. When o is already present the backing set keeps its
    ;; ORIGINAL member object (cljs assoc-existing-key semantics), so the
    ;; indexes — keyed by that stored member — must not be re-run against o,
    ;; which can be = yet property-divergent (e.g. metadata-based properties).
    (if (contains? s o)
      (IndexedPersistentSet. (-conj s o) eq uniq sorted auto)
      (IndexedPersistentSet.
        (-conj s o)
        (some-> eq (i/add-eq o o))
        (some-> uniq (i/add-uniq o o))
        (some-> sorted (i/add-sorted o o))
        auto)))

  IEmptyableCollection
  (-empty [coll]
    (IndexedPersistentSet.
      (-empty s)
      (some-> eq i/empty-indexes)
      (some-> uniq i/empty-indexes)
      (some-> sorted i/empty-sorted-indexes)
      auto))

  IEquiv
  (-equiv [coll other] (-equiv s other))

  IHash
  (-hash [coll] (-hash s))

  ISeqable
  (-seq [coll] (-seq s))

  ICounted
  (-count [coll] (-count s))

  ILookup
  (-lookup [coll v] (-lookup s v nil))
  (-lookup [coll v not-found] (-lookup s v not-found))

  ISet
  (-disjoin [coll v]
    ;; cljs set -disjoin allocates a new set even when v is absent, so the
    ;; (identical? ns s) guard the JVM impl uses never fires here — guard on
    ;; membership instead. Without it, disjoining an ABSENT element whose
    ;; property values collide with a present member would delete the live
    ;; member's index entries (del-uniq keys by property value alone).
    (if (contains? s v)
      ;; delete from the indexes using the STORED member, not the caller's key:
      ;; they are = but can yield different property values (metadata- or
      ;; type-based properties), and the indexes are keyed by what was stored.
      (let [old-element (-lookup s v nil)]
        (IndexedPersistentSet.
          (-disjoin s v)
          (some-> eq (i/del-eq old-element old-element))
          (some-> uniq (i/del-uniq old-element))
          (some-> sorted (i/del-sorted old-element old-element))
          auto))
      ;; absent: content unchanged, but mirror the host set, which still
      ;; returns a fresh (equal) set object rather than itself
      (IndexedPersistentSet. (-disjoin s v) eq uniq sorted auto)))

  IFn
  (-invoke [coll k]
    (-lookup s k))
  (-invoke [coll k not-found]
    (-lookup s k not-found)))

(es6-iterable IndexedPersistentSet)
