(ns com.wotbrew.idx.impl.map
  (:require [com.wotbrew.idx.impl.protocols :as p]
            [com.wotbrew.idx.impl.index :as i])
  (:import (clojure.lang IKVReduce IPersistentCollection ILookup IPersistentMap Associative IMeta IObj MapEquivalence IHashEq IFn Counted Seqable)
           (java.util Map$Entry Map)))

(deftype IndexedPersistentMap
  [m
   ^:volatile-mutable eq
   ^:volatile-mutable uniq
   ^:volatile-mutable sorted
   ^boolean auto]
  p/Idx
  (-rewrap [idx a] (IndexedPersistentMap. m eq uniq sorted a))
  (-get-eq [idx p]
    (or (get eq p)
        (when auto
          (let [new-index (i/create-eq-from-associative m p)]
            (locking idx
              (or (get (.-eq idx) p)
                  (do (set! (.-eq idx) (assoc (.-eq idx) p new-index))
                      new-index)))))))
  (-get-uniq [idx p]
    (or (get uniq p)
        (when auto
          (let [new-index (i/create-uniq-from-associative m p)]
            (locking idx
              (or (get (.-uniq idx) p)
                  (do (set! (.-uniq idx) (assoc (.-uniq idx) p new-index))
                      new-index)))))))
  (-get-sort [idx p]
    (or (get sorted p)
        (when auto
          (let [new-index (i/create-sorted-from-associative m p)]
            (locking idx
              (or (get (.-sorted idx) p)
                  (do (set! (.-sorted idx) (assoc (.-sorted idx) p new-index))
                      new-index)))))))
  (-add-index [idx p kind]
    (case kind
      :idx/hash
      (if (get eq p)
        idx
        (IndexedPersistentMap. m (assoc eq p (i/create-eq-from-associative m p)) uniq sorted auto))
      :idx/unique
      (if (get uniq p)
        idx
        (IndexedPersistentMap. m eq (assoc uniq p (i/create-uniq-from-associative m p)) sorted auto))
      :idx/sort
      (if (get sorted p)
        idx
        (IndexedPersistentMap. m eq uniq (assoc sorted p (i/create-sorted-from-associative m p)) auto))))
  (-del-index [idx p kind]
    (case kind
      :idx/hash
      (if (get eq p)
        (IndexedPersistentMap. m (not-empty (dissoc eq p)) uniq sorted auto)
        idx)
      :idx/unique
      (if (get uniq p)
        (IndexedPersistentMap. m eq (not-empty (dissoc uniq p)) sorted auto)
        idx)
      :idx/sort
      (if (get sorted p)
        (IndexedPersistentMap. m eq uniq (not-empty (dissoc sorted p)) auto)
        idx)))
  (-elements [idx] (vals m))
  (-id-element-pairs [idx] (map (juxt key val) m))
  p/Wrap
  (-wrap [this a]
    (if (= a auto) this (p/-rewrap this a)))
  p/Unwrap
  (-unwrap [this] m)
  Map
  (size [this] (.size ^Map m))
  (isEmpty [this] (.isEmpty ^Map m))
  (containsValue [this v] (.containsValue ^Map m v))
  (get [this k] (.get ^Map m k))
  (put [this k v] (throw (UnsupportedOperationException.)))
  (remove [this k] (throw (UnsupportedOperationException.)))
  (putAll [this m2] (throw (UnsupportedOperationException.)))
  (clear [this] (throw (UnsupportedOperationException.)))
  (keySet [this] (.keySet ^Map m))
  (values [this] (.values ^Map m))
  (entrySet [this] (.entrySet ^Map m))

  IMeta
  (meta [this] (.meta ^IMeta m))
  IObj
  (withMeta [this mta] (IndexedPersistentMap. (.withMeta ^IObj m mta) eq uniq sorted auto))

  MapEquivalence
  ;; plain persistent collections are Serializable; without this marker
  ;; ObjectOutputStream refuses the wrapper where it accepts the plain coll
  java.io.Serializable
  IHashEq
  (hasheq [this] (.hasheq ^IHashEq m))

  IFn
  ;; every arity delegates to the backing collection — including the wrong ones
  ;; — because deftype leaves unimplemented interface methods abstract, and an
  ;; AbstractMethodError is an Error (uncatchable via `catch Exception`) whereas
  ;; the plain collection throws ArityException, with a message naming the
  ;; backing class rather than this wrapper
  (invoke [this] (.invoke ^IFn m))
  (invoke [this a1] (.invoke ^IFn m a1))
  (invoke [this a1 a2] (.invoke ^IFn m a1 a2))
  (invoke [this a1 a2 a3] (.invoke ^IFn m a1 a2 a3))
  (invoke [this a1 a2 a3 a4] (.invoke ^IFn m a1 a2 a3 a4))
  (invoke [this a1 a2 a3 a4 a5] (.invoke ^IFn m a1 a2 a3 a4 a5))
  (invoke [this a1 a2 a3 a4 a5 a6] (.invoke ^IFn m a1 a2 a3 a4 a5 a6))
  (invoke [this a1 a2 a3 a4 a5 a6 a7] (.invoke ^IFn m a1 a2 a3 a4 a5 a6 a7))
  (invoke [this a1 a2 a3 a4 a5 a6 a7 a8] (.invoke ^IFn m a1 a2 a3 a4 a5 a6 a7 a8))
  (invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9] (.invoke ^IFn m a1 a2 a3 a4 a5 a6 a7 a8 a9))
  (invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10] (.invoke ^IFn m a1 a2 a3 a4 a5 a6 a7 a8 a9 a10))
  (invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11] (.invoke ^IFn m a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11))
  (invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12] (.invoke ^IFn m a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12))
  (invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13] (.invoke ^IFn m a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13))
  (invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14] (.invoke ^IFn m a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14))
  (invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15] (.invoke ^IFn m a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15))
  (invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16] (.invoke ^IFn m a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16))
  (invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16 a17] (.invoke ^IFn m a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16 a17))
  (invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16 a17 a18] (.invoke ^IFn m a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16 a17 a18))
  (invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16 a17 a18 a19] (.invoke ^IFn m a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16 a17 a18 a19))
  (invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16 a17 a18 a19 a20] (.invoke ^IFn m a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16 a17 a18 a19 a20))
  (invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16 a17 a18 a19 a20 rest] (.invoke ^IFn m a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16 a17 a18 a19 a20 rest))
  (applyTo [this arglist] (apply m arglist))

  IPersistentMap
  (assoc [this o o1]
    (if-some [entry (find m o)]
      (let [id (key entry)
            old-element (val entry)]
        (if (identical? o1 old-element)
          this
          (IndexedPersistentMap.
            (.assoc ^IPersistentMap m o o1)
            (some-> eq (i/add-eq id old-element o1))
            (some-> uniq (i/add-uniq id old-element o1))
            (some-> sorted (i/add-sorted id old-element o1))
            auto)))
      (IndexedPersistentMap.
        (.assoc ^IPersistentMap m o o1)
        (some-> eq (i/add-eq o o1))
        (some-> uniq (i/add-uniq o o1))
        (some-> sorted (i/add-sorted o o1))
        auto)))
  (assocEx [this o o1]
    (if (contains? m o)
      (throw (RuntimeException. "Key already present"))
      (assoc this o o1)))
  (without [this o]
    (if-some [entry (find m o)]
      (let [id (key entry)
            old-element (val entry)]
        (IndexedPersistentMap.
          (.without ^IPersistentMap m o)
          (some-> eq (i/del-eq id old-element))
          (some-> uniq (i/del-uniq old-element))
          (some-> sorted (i/del-sorted id old-element))
          auto))
      this))
  Counted
  Iterable
  (iterator [this] (.iterator ^Iterable m))
  Seqable
  (seq [this] (.seq ^Seqable m))
  IPersistentCollection
  (count [this] (.count ^IPersistentCollection m))
  (cons [^IPersistentMap this o]
    (cond
      (instance? Map$Entry o)
      (let [^Map$Entry e o]
        (.assoc this (.getKey e) (.getValue e)))
      (vector? o)
      (if (= 2 (count o))
        (.assoc this (nth o 0) (nth o 1))
        (throw (IllegalArgumentException. "Vector arg to map conj must be a pair")))
      (map? o) (reduce-kv assoc this o)
      :else
      (reduce
        (fn [^IPersistentMap this ^Map$Entry e]
          (.assoc this (.getKey e) (.getValue e)))
        this
        o)))
  (empty [this]
    (IndexedPersistentMap.
      (.empty ^IPersistentCollection m)
      (some-> eq i/empty-indexes)
      (some-> uniq i/empty-indexes)
      (some-> sorted i/empty-sorted-indexes)
      auto))
  (equiv [this o] (.equiv ^IPersistentCollection m o))
  ILookup
  (valAt [this o] (.valAt ^ILookup m o))
  (valAt [this o o1] (.valAt ^ILookup m o o1))
  Associative
  (containsKey [this o] (.containsKey ^Associative m o))
  (entryAt [this o] (.entryAt ^Associative m o))
  Object
  (equals [this obj] (.equals m obj))
  (hashCode [this] (.hashCode m))
  (toString [this] (.toString m))
  Callable
  (call [this] (.call ^Callable m))
  Runnable
  (run [this] (.run ^Runnable m))
  IKVReduce
  (kvreduce [this f init] (reduce-kv f init m)))
