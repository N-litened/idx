(ns com.wotbrew.idx.impl.vector
  (:require [com.wotbrew.idx.impl.protocols :as p]
            [com.wotbrew.idx.impl.index :as i])
  (:import (java.util RandomAccess List)
           (clojure.lang IHashEq Counted IObj IMeta IPersistentVector Seqable Reversible Indexed IPersistentCollection IPersistentStack ILookup Associative IFn Sequential IReduce IKVReduce Util)))

(deftype IndexedPersistentVector
  [v
   ^:volatile-mutable eq
   ^:volatile-mutable uniq
   ^:volatile-mutable sorted
   ^boolean auto]
  p/Idx
  (-rewrap [idx a] (IndexedPersistentVector. v eq uniq sorted a))
  (-get-eq [idx p]
    (or (get eq p)
        (when auto
          (let [new-index (i/create-eq-from-associative v p)]
            ;; Index construction may race, but publication must merge with
            ;; indexes another thread has already published. A plain mutable
            ;; read/assoc/write can lose the other property entirely.
            (locking idx
              (or (get (.-eq idx) p)
                  (do (set! (.-eq idx) (assoc (.-eq idx) p new-index))
                      new-index)))))))
  (-get-uniq [idx p]
    (or (get uniq p)
        (when auto
          (let [new-index (i/create-uniq-from-associative v p)]
            (locking idx
              (or (get (.-uniq idx) p)
                  (do (set! (.-uniq idx) (assoc (.-uniq idx) p new-index))
                      new-index)))))))
  (-get-sort [idx p]
    (or (get sorted p)
        (when auto
          (let [new-index (i/create-sorted-from-associative v p)]
            (locking idx
              (or (get (.-sorted idx) p)
                  (do (set! (.-sorted idx) (assoc (.-sorted idx) p new-index))
                      new-index)))))))
  (-add-index [idx p kind]
    (case kind
      :idx/hash
      (if (get eq p)
        idx
        (IndexedPersistentVector. v (assoc eq p (i/create-eq-from-associative v p)) uniq sorted auto))
      :idx/unique
      (if (get uniq p)
        idx
        (IndexedPersistentVector. v eq (assoc uniq p (i/create-uniq-from-associative v p)) sorted auto))
      :idx/sort
      (if (get sorted p)
        idx
        (IndexedPersistentVector. v eq uniq (assoc sorted p (i/create-sorted-from-associative v p)) auto))))
  (-del-index [idx p kind]
    (case kind
      :idx/hash
      (if (get eq p)
        (IndexedPersistentVector. v (not-empty (dissoc eq p)) uniq sorted auto)
        idx)
      :idx/unique
      (if (get uniq p)
        (IndexedPersistentVector. v eq (not-empty (dissoc uniq p)) sorted auto)
        idx)
      :idx/sort
      (if (get sorted p)
        (IndexedPersistentVector. v eq uniq (not-empty (dissoc sorted p)) auto)
        idx)))
  (-elements [idx] v)
  (-id-element-pairs [idx] (map-indexed vector v))
  p/Wrap
  (-wrap [this a]
    (if (= a auto) this (p/-rewrap this a)))
  p/Unwrap
  (-unwrap [this] v)
  RandomAccess
  Comparable
  (compareTo [this o] (.compareTo ^Comparable v o))
  IHashEq
  (hasheq [this] (.hasheq ^IHashEq v))
  List
  (size [this] (.size ^List v))
  (isEmpty [this] (.isEmpty ^List v))
  (contains [this o] (.contains ^List v o))
  (toArray [this] (.toArray ^List v))
  (^objects toArray [this ^objects a] (.toArray ^List v a))
  (add [this o] (throw (UnsupportedOperationException.)))
  (^boolean remove [this ^Object o] (throw (UnsupportedOperationException.)))
  (^Object remove [this ^int o] (throw (UnsupportedOperationException.)))
  (containsAll [this coll] (.containsAll ^List v coll))
  (addAll [this coll] (throw (UnsupportedOperationException.)))
  (addAll [this i coll] (throw (UnsupportedOperationException.)))
  (removeAll [this coll] (throw (UnsupportedOperationException.)))
  (retainAll [this coll] (throw (UnsupportedOperationException.)))
  (clear [this] (throw (UnsupportedOperationException.)))
  (get [this i] (.get ^List v i))
  (indexOf [this o] (.indexOf ^List v o))
  (lastIndexOf [this o] (.lastIndexOf ^List v o))
  (listIterator [this] (.listIterator ^List v))
  (listIterator [this i] (.listIterator ^List v i))
  (subList [this from to] (.subList ^List v from to ))
  (set [this i o] (throw (UnsupportedOperationException.)))
  (add [this i o] (throw (UnsupportedOperationException.)))
  Iterable
  (iterator [this] (.iterator ^Iterable v))
  Counted
  IObj
  (withMeta [this meta] (IndexedPersistentVector. (.withMeta ^IObj v meta) eq uniq sorted auto))
  IMeta
  (meta [this] (.meta ^IMeta v))
  IPersistentVector
  (length [this] (.length ^IPersistentVector v))
  (assocN [this i val]
    (let [append? (= i (count v))
          old-element (when-not append? (nth v i))]
      (cond
        (and (not append?) (identical? val old-element)) this

        append?
        (IndexedPersistentVector.
          (.assocN ^IPersistentVector v i val)
          (some-> eq (i/add-eq i val))
          (some-> uniq (i/add-uniq i val))
          (some-> sorted (i/add-sorted i val))
          auto)

        :else
        (IndexedPersistentVector.
          (.assocN ^IPersistentVector v i val)
          (some-> eq (i/add-eq i old-element val))
          (some-> uniq (i/add-uniq i old-element val))
          (some-> sorted (i/add-sorted i old-element val))
          auto))))
  (cons [this val]
    (let [i (.length ^IPersistentVector v)]
      (IndexedPersistentVector.
        (.cons ^IPersistentVector v val)
        (some-> eq (i/add-eq i val))
        (some-> uniq (i/add-uniq i val))
        (some-> sorted (i/add-sorted i val))
        auto)))
  Seqable
  (seq [this] (.seq ^Seqable v))
  Reversible
  (rseq [this] (.rseq ^Reversible v))
  Indexed
  (nth [this i] (.nth ^Indexed v i))
  (nth [this i notFound] (.nth ^Indexed v i notFound))
  IPersistentCollection
  (count [this] (.count ^IPersistentCollection v))
  (empty [this]
    (IndexedPersistentVector.
      (.empty ^IPersistentCollection v)
      (some-> eq i/empty-indexes)
      (some-> uniq i/empty-indexes)
      (some-> sorted i/empty-sorted-indexes)
      auto))
  (equiv [this o] (.equiv ^IPersistentCollection v o))
  IPersistentStack
  (peek [this] (.peek ^IPersistentStack v))
  (pop [this]
    (let [i (dec (.length ^IPersistentVector v))
          old-element (if (neg? i) nil (nth v i))]
      (IndexedPersistentVector.
        (pop ^IPersistentStack v)
        (some-> eq (i/del-eq i old-element))
        (some-> uniq (i/del-uniq old-element))
        (some-> sorted (i/del-sorted i old-element))
        auto)))
  ILookup
  (valAt [this key] (.valAt ^ILookup v key))
  (valAt [this key notFound] (.valAt ^ILookup v key notFound))
  Associative
  (containsKey [this key] (.containsKey ^Associative v key))
  (entryAt [this key] (.entryAt ^Associative v key))
  (assoc [this key val]
    (if (Util/isInteger key)
      (.assocN ^IPersistentVector this (.intValue ^Number key) val)
      (throw (IllegalArgumentException. "Key must be integer"))))
  Object
  (hashCode [this] (.hashCode v))
  (equals [this obj] (.equals v obj))
  (toString [this] (.toString v))
  Sequential
  ;; plain persistent collections are Serializable; without this marker
  ;; ObjectOutputStream refuses the wrapper where it accepts the plain coll
  java.io.Serializable
  IFn
  ;; every arity delegates to the backing collection — including the wrong ones
  ;; — because deftype leaves unimplemented interface methods abstract, and an
  ;; AbstractMethodError is an Error (uncatchable via `catch Exception`) whereas
  ;; the plain collection throws ArityException, with a message naming the
  ;; backing class rather than this wrapper
  (invoke [this] (.invoke ^IFn v))
  (invoke [this a1] (.invoke ^IFn v a1))
  (invoke [this a1 a2] (.invoke ^IFn v a1 a2))
  (invoke [this a1 a2 a3] (.invoke ^IFn v a1 a2 a3))
  (invoke [this a1 a2 a3 a4] (.invoke ^IFn v a1 a2 a3 a4))
  (invoke [this a1 a2 a3 a4 a5] (.invoke ^IFn v a1 a2 a3 a4 a5))
  (invoke [this a1 a2 a3 a4 a5 a6] (.invoke ^IFn v a1 a2 a3 a4 a5 a6))
  (invoke [this a1 a2 a3 a4 a5 a6 a7] (.invoke ^IFn v a1 a2 a3 a4 a5 a6 a7))
  (invoke [this a1 a2 a3 a4 a5 a6 a7 a8] (.invoke ^IFn v a1 a2 a3 a4 a5 a6 a7 a8))
  (invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9] (.invoke ^IFn v a1 a2 a3 a4 a5 a6 a7 a8 a9))
  (invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10] (.invoke ^IFn v a1 a2 a3 a4 a5 a6 a7 a8 a9 a10))
  (invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11] (.invoke ^IFn v a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11))
  (invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12] (.invoke ^IFn v a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12))
  (invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13] (.invoke ^IFn v a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13))
  (invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14] (.invoke ^IFn v a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14))
  (invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15] (.invoke ^IFn v a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15))
  (invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16] (.invoke ^IFn v a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16))
  (invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16 a17] (.invoke ^IFn v a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16 a17))
  (invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16 a17 a18] (.invoke ^IFn v a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16 a17 a18))
  (invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16 a17 a18 a19] (.invoke ^IFn v a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16 a17 a18 a19))
  (invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16 a17 a18 a19 a20] (.invoke ^IFn v a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16 a17 a18 a19 a20))
  (invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16 a17 a18 a19 a20 rest] (.invoke ^IFn v a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16 a17 a18 a19 a20 rest))
  (applyTo [this arglist] (apply v arglist))
  Callable
  (call [this] (.call ^Callable v))
  Runnable
  (run [this] (.run ^Runnable v))
  ;; IReduce, not just IReduceInit: clojure.core.protocols implements no-init
  ;; (reduce f coll) for IReduceInit collections with an unconditional cast to
  ;; IReduce, so implementing only IReduceInit makes (reduce + wrapper) throw
  ;; ClassCastException where the plain vector works
  IReduce
  (reduce [this f] (reduce f v))
  (reduce [this f init] (reduce f init v))
  IKVReduce
  (kvreduce [this f init] (reduce-kv f init v)))
