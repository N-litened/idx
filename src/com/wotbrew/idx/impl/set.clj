(ns com.wotbrew.idx.impl.set
  (:require [com.wotbrew.idx.impl.protocols :as p]
            [com.wotbrew.idx.impl.index :as i])
  (:import (clojure.lang IPersistentSet IPersistentCollection Seqable IFn IHashEq IMeta IObj)
           (java.util Set Collection)))

(deftype IndexedPersistentSet
  [s
   ^:unsynchronized-mutable eq
   ^:unsynchronized-mutable uniq
   ^:unsynchronized-mutable sorted
   ^boolean auto]
  p/Idx
  (-rewrap [idx a] (IndexedPersistentSet. s eq uniq sorted a))
  (-get-eq [idx p]
    (or (when (some? eq) (eq p))
        (when auto
          (let [i (i/create-eq-from-elements s p)
                eq (assoc eq p i)]
            (set! (.-eq idx) eq)
            i))))
  (-get-uniq [idx p]
    (or (when (some? uniq) (uniq p))
        (when auto
          (let [i (i/create-unique-from-elements s p)
                uniq (assoc uniq p i)]
            (set! (.-uniq idx) uniq)
            i))))
  (-get-sort [idx p]
    (or (when (some? sorted) (sorted p))
        (when auto
          (let [i (i/create-sorted-from-elements s p)
                sorted (assoc sorted p i)]
            (set! (.-sorted idx) sorted)
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
        (IndexedPersistentSet. s (dissoc eq p) uniq sorted auto)
        idx)
      :idx/unique
      (if (get uniq p)
        (IndexedPersistentSet. s eq (dissoc uniq p) sorted auto)
        idx)
      :idx/sort
      (if (get sorted p)
        (IndexedPersistentSet. s eq uniq (dissoc sorted p) auto)
        idx)))
  (-elements [idx] s)
  (-id-element-pairs [idx] (map (fn [x] [x x]) s))
  p/Wrap
  (-wrap [this a]
    (if (= a auto) this (p/-rewrap this a)))
  p/Unwrap
  (-unwrap [this] s)
  IObj
  (withMeta [this meta] (IndexedPersistentSet. (.withMeta ^IObj s meta) eq uniq sorted auto))
  IMeta
  (meta [this] (.meta ^IMeta s))
  Collection
  ;; plain persistent collections are Serializable; without this marker
  ;; ObjectOutputStream refuses the wrapper where it accepts the plain coll
  java.io.Serializable
  IHashEq
  (hasheq [this] (.hasheq ^IHashEq s))
  IFn
  ;; every arity delegates to the backing collection — including the wrong ones
  ;; — because deftype leaves unimplemented interface methods abstract, and an
  ;; AbstractMethodError is an Error (uncatchable via `catch Exception`) whereas
  ;; the plain collection throws ArityException, with a message naming the
  ;; backing class rather than this wrapper
  (invoke [this] (.invoke ^IFn s))
  (invoke [this a1] (.invoke ^IFn s a1))
  (invoke [this a1 a2] (.invoke ^IFn s a1 a2))
  (invoke [this a1 a2 a3] (.invoke ^IFn s a1 a2 a3))
  (invoke [this a1 a2 a3 a4] (.invoke ^IFn s a1 a2 a3 a4))
  (invoke [this a1 a2 a3 a4 a5] (.invoke ^IFn s a1 a2 a3 a4 a5))
  (invoke [this a1 a2 a3 a4 a5 a6] (.invoke ^IFn s a1 a2 a3 a4 a5 a6))
  (invoke [this a1 a2 a3 a4 a5 a6 a7] (.invoke ^IFn s a1 a2 a3 a4 a5 a6 a7))
  (invoke [this a1 a2 a3 a4 a5 a6 a7 a8] (.invoke ^IFn s a1 a2 a3 a4 a5 a6 a7 a8))
  (invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9] (.invoke ^IFn s a1 a2 a3 a4 a5 a6 a7 a8 a9))
  (invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10] (.invoke ^IFn s a1 a2 a3 a4 a5 a6 a7 a8 a9 a10))
  (invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11] (.invoke ^IFn s a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11))
  (invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12] (.invoke ^IFn s a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12))
  (invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13] (.invoke ^IFn s a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13))
  (invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14] (.invoke ^IFn s a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14))
  (invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15] (.invoke ^IFn s a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15))
  (invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16] (.invoke ^IFn s a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16))
  (invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16 a17] (.invoke ^IFn s a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16 a17))
  (invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16 a17 a18] (.invoke ^IFn s a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16 a17 a18))
  (invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16 a17 a18 a19] (.invoke ^IFn s a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16 a17 a18 a19))
  (invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16 a17 a18 a19 a20] (.invoke ^IFn s a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16 a17 a18 a19 a20))
  (invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16 a17 a18 a19 a20 rest] (.invoke ^IFn s a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16 a17 a18 a19 a20 rest))
  (applyTo [this arglist] (apply s arglist))
  Callable
  (call [this] (.call ^Callable s))
  Runnable
  (run [this] (.run ^Runnable s))
  Set
  (size [this] (.size ^Set s))
  (isEmpty [this] (.isEmpty ^Set s))
  (iterator [this] (.iterator ^Set s))
  (toArray [this] (.toArray ^Set s))
  (^objects toArray [this ^objects a] (.toArray ^Set s a))
  (add [this e] (throw (UnsupportedOperationException.)))
  (remove [this o] (throw (UnsupportedOperationException.)))
  (containsAll [this p] (.containsAll ^Set s p))
  (addAll [this p] (throw (UnsupportedOperationException.)))
  (removeAll [this p] (throw (UnsupportedOperationException.)))
  (retainAll [this p] (throw (UnsupportedOperationException.)))
  (clear [this] (throw (UnsupportedOperationException.)))
  IPersistentSet
  (disjoin [this key]
    (let [ns (.disjoin ^IPersistentSet s key)]
      (if (identical? ns s)
        this
        ;; delete from the indexes using the STORED member, not the caller's
        ;; key: they are = but can yield different property values (metadata- or
        ;; type-based properties), and the indexes are keyed by what was stored.
        ;; The vector/map impls resolve the stored element the same way.
        (let [old-element (.get ^IPersistentSet s key)]
          (IndexedPersistentSet.
            ns
            (some-> eq (i/del-eq old-element old-element))
            (some-> uniq (i/del-uniq old-element))
            (some-> sorted (i/del-sorted old-element old-element))
            auto)))))
  (contains [this key] (.contains ^IPersistentSet s key))
  (get [this key] (.get ^IPersistentSet s key))
  Seqable
  (seq [this] (.seq ^IPersistentSet s))
  IPersistentCollection
  (count [this] (.count ^IPersistentSet s))
  (cons [this o]
    (let [ns (.cons ^IPersistentSet s o)]
      (if (identical? ns s)
        this
        (IndexedPersistentSet.
          ns
          (some-> eq (i/add-eq o o))
          (some-> uniq (i/add-uniq o o))
          (some-> sorted (i/add-sorted o o))
          auto))))
  (empty [this]
    (IndexedPersistentSet.
      (.empty ^IPersistentSet s)
      (some-> eq i/empty-indexes)
      (some-> uniq i/empty-indexes)
      (some-> sorted i/empty-sorted-indexes)
      auto))
  (equiv [this o] (.equiv ^IPersistentSet s o))
  Object
  (hashCode [this] (.hashCode s))
  (equals [this obj] (.equals s obj))
  (toString [this] (.toString s)))