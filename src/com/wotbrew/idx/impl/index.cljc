(ns com.wotbrew.idx.impl.index
  (:require [com.wotbrew.idx.impl.protocols :as p]))

;; for eq/sorted leaves we use maps rather than sets so
;; we get the PersistentArrayMap optimisation when small.

(defn create-eq-from-associative
  [m p]
  (let [rf (fn [m id v]
             (let [ival (p/-property p v)]
               (assoc! m ival (assoc (get m ival {}) id v))))]
    (persistent! (reduce-kv rf (transient {}) m))))

(defn create-eq-from-elements
  [coll p]
  (let [rf (fn [m [id v]]
             (let [ival (p/-property p v)]
               (assoc! m ival (assoc (get m ival {}) id v))))]
    (persistent! (reduce rf (transient {}) (p/-id-element-pairs coll)))))

(defn- uniqueness-violation
  ([p v id]
   (ex-info "cannot build :idx/unique index: property is not unique across the collection"
            {:idx/property p, :idx/value v, :idx/id id}))
  ([p v existing-id id]
   (ex-info "unique index violation: another element already has this property value"
            {:idx/property p, :idx/value v, :idx/existing-id existing-id, :idx/id id})))

;; :idx/unique enforces uniqueness: index construction and every modification
;; throw on a violation instead of silently last-write-winning (which used to
;; leave the index permanently corrupted once either duplicate was touched).
;; This applies to auto-realised unique indexes too — identify/pk/replace-by on
;; an auto collection declare the queried property unique. Plain (unwrapped)
;; collections keep plain first-match linear scans.

(defn create-uniq-from-associative
  [m p]
  (let [rf (fn [t id v]
             (let [ival (p/-property p v)
                   n (count t)
                   t (assoc! t ival id)]
               ;; duplicates are detected by the count not growing after
               ;; assoc!, NOT by probing with a not-found sentinel: values and
               ;; ids are arbitrary user data, so no sentinel is collision-free
               ;; (that bug class was purged from this library once already)
               (if (== n (count t))
                 (throw (uniqueness-violation p ival id))
                 t)))]
    (persistent! (reduce-kv rf (transient {}) m))))

(defn create-unique-from-elements
  [coll p]
  (let [rf (fn [t [id v]]
             (let [ival (p/-property p v)
                   n (count t)
                   t (assoc! t ival id)]
               ;; see create-uniq-from-associative for the count-based check
               (if (== n (count t))
                 (throw (uniqueness-violation p ival id))
                 t)))]
    (persistent! (reduce rf (transient {}) (p/-id-element-pairs coll)))))

(defn create-sorted-from-associative
  [m p]
  (let [rf (fn [m id v]
             (let [ival (p/-property p v)]
               (assoc m ival (assoc (get m ival {}) id v))))]
    (reduce-kv rf (sorted-map) m)))

(defn create-sorted-from-elements
  [coll p]
  (let [rf (fn [m [id v]]
             (let [ival (p/-property p v)]
               (assoc m ival (assoc (get m ival {}) id v))))]
    (reduce rf (sorted-map) (p/-id-element-pairs coll))))

(defn add-eq
  ([eq id element]
   (reduce-kv
     (fn [eq p i]
       (let [v (p/-property p element)
             nset (i v {})
             nset (assoc nset id element)
             i (assoc i v nset)]
         (assoc eq p i)))
     eq
     eq))
  ([eq id old-element element]
   (reduce-kv
     (fn [eq p i]
       (let [ov (p/-property p old-element)
             v (p/-property p element)]
         (if (= ov v)
           ;; the indexed value is unchanged, but the stored element must still be replaced
           (assoc eq p (assoc i v (assoc (get i v {}) id element)))
           (let [oset (i ov {})
                 oset (dissoc oset id)
                 i (if (empty? oset) (dissoc i ov) (assoc i ov oset))

                 nset (get i v {})
                 nset (assoc nset id element)
                 i (assoc i v nset)]
             (assoc eq p i)))))
     eq
     eq)))

(defn del-eq [eq id old-element]
  (reduce-kv
    (fn [eq p i]
      (let [ov (p/-property p old-element)
            oset (i ov {})
            oset (dissoc oset id)
            i (if (empty? oset) (dissoc i ov) (assoc i ov oset))]
        ;; keep the (possibly empty) index so it continues to be maintained
        (assoc eq p i)))
    eq
    eq))

(defn add-uniq
  ([unq id element]
   (reduce-kv
     (fn [unq p i]
       (let [v (p/-property p element)]
         ;; find (not get/sentinel): ids can legitimately be nil (nil map keys)
         (if-some [kv (find i v)]
           (if (= (val kv) id)
             (assoc unq p (assoc i v id))
             (throw (uniqueness-violation p v (val kv) id)))
           (assoc unq p (assoc i v id)))))
     unq
     unq))
  ([unq id old-element element]
   (reduce-kv
     (fn [unq p i]
       (let [ov (p/-property p old-element)
             v (p/-property p element)]
         (cond
           (and (= id (i v)) (= ov v)) unq
           ;; replacing the element that owns this value with an equal-valued
           ;; one — the owner keeps the slot, never a violation
           (= ov v) (assoc unq p (assoc i v id))
           :else
           ;; value changed: the new value's slot must be free (or already ours)
           (if-some [kv (find i v)]
             (if (= (val kv) id)
               (assoc unq p (-> i (dissoc ov) (assoc v id)))
               (throw (uniqueness-violation p v (val kv) id)))
             (assoc unq p (-> i (dissoc ov) (assoc v id)))))))
     unq
     unq)))

(defn del-uniq [unq old-element]
  (reduce-kv
    (fn [unq p i]
      (let [ov (p/-property p old-element)]
        ;; keep the (possibly empty) index so it continues to be maintained
        (assoc unq p (dissoc i ov))))
    unq
    unq))

(defn add-sorted
  ([srt id element]
   (reduce-kv
     (fn [srt p i]
       (let [v (p/-property p element)
             nset (i v {})
             nset (assoc nset id element)
             i (assoc i v nset)]
         (assoc srt p i)))
     srt
     srt))
  ([srt id old-element element]
   (reduce-kv
     (fn [srt p i]
       (let [ov (p/-property p old-element)
             v (p/-property p element)]
         (if (= ov v)
           ;; the indexed value is unchanged, but the stored element must still be replaced
           (assoc srt p (assoc i v (assoc (get i v {}) id element)))
           (let [oset (get i ov {})
                 oset (dissoc oset id)
                 i (if (empty? oset) (dissoc i ov) (assoc i ov oset))

                 nset (get i v {})
                 nset (assoc nset id element)
                 i (assoc i v nset)]
             (assoc srt p i)))))
     srt
     srt)))

(defn del-sorted [srt id old-element]
  (reduce-kv
    (fn [srt p i]
      (let [ov (p/-property p old-element)
            oset (i ov {})
            oset (dissoc oset id)
            i (if (empty? oset) (dissoc i ov) (assoc i ov oset))]
        ;; keep the (possibly empty) index so it continues to be maintained
        (assoc srt p i)))
    srt
    srt))

(defn empty-indexes
  "Returns the eq/uniq index map with each per-property index emptied,
  preserving which properties are indexed."
  [im]
  (reduce-kv (fn [m p _] (assoc m p {})) {} im))

(defn empty-sorted-indexes
  "Returns the sorted index map with each per-property index emptied,
  preserving which properties are indexed."
  [im]
  (reduce-kv (fn [m p _] (assoc m p (sorted-map))) {} im))