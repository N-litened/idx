(ns com.wotbrew.idx-test
  (:require [clojure.test :refer [deftest is]]
            [com.wotbrew.idx :refer [lookup lookup-keys unwrap auto identify pred match index delete-index path
                                     replace-by as-key ascending descending pk pcomp]]
            [com.wotbrew.idx.impl.protocols :as p]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check :as tc]))

(deftest indexed-vector-test
  (let [v (vec (range 100))]

    (is (vector? (auto v)))
    (is (identical? v (unwrap (auto v))))
    (is (= {:foo 42} (meta (auto (with-meta v {:foo 42})))))

    (let [v2 (auto v)]
      (is (= v2 v))
      (is (= (conj v -1) (conj v2 -1)))
      (is (= (assoc v 0 100) (assoc v2 0 100)))
      (is (= (count v) (count v2)))
      (is (= (seq v) (seq v2)))

      (is (= (filter even? v) (sort (lookup v2 (pred even?)))))
      (is (sequential? (lookup v2 (pred even?))))
      (is (identical? (p/-get-index v2 (pred even?) :idx/hash) (p/-get-index v2 (pred even?) :idx/hash)))
      (is (= (filter even? v) (sort (lookup v2 (pred even?)))))
      (is (= (sort (filter even? (conj v 12))) (sort (lookup (conj v2 12) (pred even?)))))

      (is (= 4 (identify v2 identity 4)))
      (is (= 4 (identify v2 inc 5)))

      (is (= (filter even? (pop v)) (sort (lookup (pop v2) (pred even?)))))
      (is (= (pop v) (pop v2)))
      (is (= (peek v) (peek v2))))))

(deftest indexed-set-test
  (let [s (set (range 100))]

    (is (set? (auto s)))
    (is (identical? s (unwrap (auto s))))
    (is (= {:foo 42} (meta (auto (with-meta s {:foo 42})))))

    (let [s2 (auto s)]
      (is (= s2 s))
      (is (= (conj s -1) (conj s2 -1)))
      (is (= (count s) (count s2)))
      (is (= (seq s) (seq s2)))

      (is (= (sort (filter even? s)) (sort (lookup s2 (pred even?)))))
      (is (sequential? (lookup s2 (pred even?))))
      (is (identical? (p/-get-index s2 (pred even?) :idx/hash) (p/-get-index s2 (pred even?) :idx/hash)))
      (is (= (sort (filter even? s)) (sort (lookup s2 (pred even?)))))
      (is (= (sort (filter even? (conj s 12))) (sort (lookup (conj s2 12) (pred even?)))))

      (is (= 4 (identify s2 identity 4)))
      (is (= 4 (identify s2 inc 5)))

      (is (= (sort (filter even? (disj s 99))) (sort (lookup (disj s2 99) (pred even?))))))))

(deftest indexed-map-test
  (let [m (zipmap (range 100) (range 100))]

    (is (map? (auto m)))
    (is (identical? m (unwrap (auto m))))
    (is (= {:foo 42} (meta (auto (with-meta m {:foo 42})))))

    (let [m2 (auto m)]
      (is (= m2 m))
      (is (= (conj m {-1 -1}) (conj m2 {-1 -1})))
      (is (= (count m) (count m2)))
      (is (= (seq m) (seq m2)))

      (is (= (sort (filter even? (vals m))) (sort (lookup m2 (pred even?)))))
      (is (sequential? (lookup m2 (pred even?))))
      (is (identical? (p/-get-index m2 even? :idx/hash) (p/-get-index m2 even? :idx/hash)))
      (is (= (sort (filter even? (vals m))) (sort (lookup m2 (pred even?)))))
      (is (= (sort (filter even? (vals (assoc m 12 12)))) (sort (lookup (assoc m2 12 12) (pred even?)))))

      (is (= 4 (identify m2 identity 4)))
      (is (= 4 (identify m2 inc 5)))

      (is (= (sort (filter even? (vals (dissoc m 99)))) (sort (lookup (dissoc m2 99) (pred even?))))))))

(deftest path-test
  (let [v (auto [{:foo {:bar 42}} {:foo {:bar 43}}])]
    (is (= {:foo {:bar 42}} (identify v (path :foo :bar) 42)))
    (is (= [{:foo {:bar 43}}] (lookup v (path :foo :bar) 43)))))

(deftest match-test
  (let [v (auto [{:foo 42,:bar 43, :baz 45} {:foo 46, :bar 47, :baz 48}])]
    (is (= {:foo 42,:bar 43, :baz 45} (identify v (match :foo 42, :bar 43))))
    (is (= {:foo 42,:bar 43, :baz 45} (identify v (match :foo 42))))
    (is (= {:foo 42,:bar 43, :baz 45} (identify v (match :foo (pred even?) :bar 43))))
    (is (= {:foo 42,:bar 43, :baz 45} (identify v (match :foo (match number? true odd? false) :bar 43))))
    (is (= {:foo 42, :bar 43 :baz 45} (identify v (match :foo 42, :baz 45, :bar 43))))
    (is (nil? (identify v (match :foo 100))))))

(deftest as-key-test
  (is (= {even? 4} (identify [{even? 4} {even? 5}] (as-key even?) 4)))
  (is (= {even? 4} (identify (auto [{even? 4} {even? 5}]) (as-key even?) 4))))

(deftest idx-test
  (is (= {42 {0 {:foo 42}} 43 {1 {:foo 43}}} (p/-get-index (index [{:foo 42} {:foo 43}] :foo :idx/hash) :foo :idx/hash)))
  (is (= {42 0 43 1} (p/-get-index (index [{:foo 42} {:foo 43}] :foo :idx/unique) :foo :idx/unique)))
  (is (= {42 {0 {:foo 42}} 43 {1 {:foo 43}}} (p/-get-index (index [{:foo 42} {:foo 43}] :foo :idx/sort) :foo :idx/sort)))
  (is (sorted? (p/-get-index (index [{:foo 42} {:foo 43}] :foo :idx/sort) :foo :idx/sort))))

(deftest replace-by-test
  (is (= [42 43] (replace-by [41 43] identity 41 42)))
  (is (= [42 43] (replace-by (auto [41 43]) identity 41 42))))

(deftest ascending-test
  (is (= [0 1 2] (ascending [0 1 2 3] identity < 3)))
  (is (= [0 1 2] (ascending (auto [0 1 2 3]) identity < 3)))
  (is (= [0 1 2] (ascending #{0 1 2 3} identity < 3)))
  (is (= [0 1 2] (ascending (auto #{0 1 2 3}) identity < 3)))
  (is (= [0 1 2] (ascending {:foo 0 :bar 1 :baz 2 :qux 3} identity < 3)))
  (is (= [0 1 2] (ascending (auto {:foo 0 :bar 1 :baz 2 :qux 3}) identity < 3))))

(deftest descending-test
  (is (= [2 1 0] (descending [0 1 2 3] identity < 3)))
  (is (= [2 1 0] (descending (auto [0 1 2 3]) identity < 3)))
  (is (= [2 1 0] (descending #{0 1 2 3} identity < 3)))
  (is (= [2 1 0] (descending (auto #{0 1 2 3}) identity < 3)))
  (is (= [2 1 0] (descending {:foo 0, :bar 1, :baz 2 :qux 3} identity < 3)))
  (is (= [2 1 0] (descending (auto {:foo 0, :bar 1, :baz 2 :qux 3}) identity < 3))))

(deftest conj-on-map-entry-test
  (is (= {42 43} (conj (auto {}) (first {42 43})))))

(deftest conj-on-seq-of-entries-test
  (is (= {42 43, 43 45} (conj (auto {}) (seq {42 43, 43 45})))))

(deftest lookup-keys-test
  (let [coll [{:age 10} {:age 21} {:age 10}]]
    ;; plain collections fall back to scanning
    (is (= #{0 2} (set (lookup-keys coll :age 10))))
    (is (= [:a] (vec (lookup-keys {:a {:age 10} :b {:age 20}} :age 10))))
    ;; indexed collections
    (is (= #{0 2} (set (lookup-keys (auto coll) :age 10))))
    (is (= #{0 2} (set (lookup-keys (index coll :age :idx/hash) :age 10))))
    ;; 2-ary predicate form and pred-in-value-position form
    (is (= #{0 2} (set (lookup-keys (auto coll) (match :age 10)))))
    (is (= #{0 2} (set (lookup-keys coll :age (pred even?)))))
    (is (= #{0 2} (set (lookup-keys (auto coll) :age (pred even?)))))))

(deftest pk-test
  ;; examples from the README
  (is (= 2 (pk [1 4 5 3] identity 5)))
  (is (= :bar (pk {:foo 42, :bar 33} identity 33)))
  (is (= 0 (pk [{:foo 42}, {:foo 33}] :foo 42)))
  (is (= 2 (pk (auto [1 4 5 3]) identity 5)))
  (is (= :bar (pk (auto {:foo 42, :bar 33}) identity 33)))
  ;; pk must return the key, not the element, when given a predicate in the value position
  (is (= 1 (pk [{:n 1} {:n 2}] :n (pred even?))))
  (is (= 1 (pk (auto [{:n 1} {:n 2}]) :n (pred even?))))
  ;; ... and when given a predicate in the 2-ary position
  (is (= 1 (pk [{:n 1} {:n 2}] (match :n 2))))
  (is (= 1 (pk (auto [{:n 1} {:n 2}]) (match :n 2)))))

(deftest identify-absent-value-test
  (let [m (index {nil {:id 1} :k {:id 2}} :id :idx/unique)]
    ;; hits under nil map keys are found
    (is (= {:id 1} (identify m :id 1)))
    (is (= {:id 2} (identify m :id 2)))
    ;; a miss must return nil, not the element stored under the nil key
    (is (nil? (identify m :id 99))))
  (is (nil? (identify (index [{:id 1}] :id :idx/unique) :id 99)))
  (is (nil? (identify (auto [{:id 1}]) :id 99))))

(deftest replace-by-missing-test
  ;; when nothing matches, the collection is returned unchanged
  (is (= [{:id 1}] (replace-by (index [{:id 1}] :id :idx/unique) :id 99 {:id 99})))
  (is (= [{:id 1}] (replace-by [{:id 1}] :id 99 {:id 99})))
  (is (= {:a {:id 1}} (replace-by (index {:a {:id 1}} :id :idx/unique) :id 99 {:id 99})))
  (is (= {:a {:id 1}} (replace-by {:a {:id 1}} :id 99 {:id 99})))
  (is (= #{{:id 1}} (replace-by (index #{{:id 1}} :id :idx/unique) :id 99 {:id 99})))
  (is (= #{{:id 1}} (replace-by #{{:id 1}} :id 99 {:id 99}))))

(deftest replace-by-hit-test
  (let [m (index {:a {:id 1 :v 1}} :id :idx/unique)
        m2 (replace-by m :id 1 {:id 1 :v 2})]
    (is (= {:a {:id 1 :v 2}} m2))
    (is (= {:id 1 :v 2} (identify m2 :id 1))))
  (let [s (index #{{:id 1 :v 1}} :id :idx/unique)
        s2 (replace-by s :id 1 {:id 1 :v 2})]
    (is (= #{{:id 1 :v 2}} s2))
    (is (= {:id 1 :v 2} (identify s2 :id 1))))
  (let [v (index [{:id 1 :v 1} {:id 2 :v 1}] :id :idx/unique)
        v2 (replace-by v :id 2 {:id 2 :v 9})]
    (is (= [{:id 1 :v 1} {:id 2 :v 9}] v2))
    (is (= {:id 2 :v 9} (identify v2 :id 2)))))

(deftest index-match-test
  ;; the README documents (index coll (match p :idx/value ...) kind)
  (let [v (index [{:foo 1 :bar 1} {:foo 1 :bar 2}] (match :foo :idx/value :bar :idx/value) :idx/hash)]
    ;; the index is actually created & used (no auto, so -get-eq must find a manual index)
    (is (some? (p/-get-eq v (p/-prop (match :foo 1 :bar 2)))))
    (is (= [{:foo 1 :bar 2}] (vec (lookup v (match :foo 1 :bar 2)))))
    (is (= #{{:foo 1 :bar 1} {:foo 1 :bar 2}} (set (lookup v (match :foo 1)))))
    (is (empty? (lookup v (match :foo 2 :bar 2)))))
  ;; a single-property match shares the plain property index
  (let [v (index [{:foo 1} {:foo 2}] (match :foo :idx/value) :idx/hash)]
    (is (some? (p/-get-eq v :foo)))
    (is (= [{:foo 2}] (vec (lookup v :foo 2))))
    (is (= [{:foo 2}] (vec (lookup v (match :foo 2)))))))

(deftest stale-element-after-assoc-test
  ;; when a replacement element has the same value for an indexed property,
  ;; the index must still serve the new element, not the old one
  (let [v (index [{:id 1 :name "a"}] :id :idx/hash :id :idx/sort)
        v2 (assoc v 0 {:id 1 :name "b"})]
    (is (= [{:id 1 :name "b"}] (vec (lookup v2 :id 1))))
    (is (= [{:id 1 :name "b"}] (vec (ascending v2 :id >= 0)))))
  (let [m (index {:a {:id 1 :name "a"}} :id :idx/hash :id :idx/sort)
        m2 (assoc m :a {:id 1 :name "b"})]
    (is (= [{:id 1 :name "b"}] (vec (lookup m2 :id 1))))
    (is (= [{:id 1 :name "b"}] (vec (ascending m2 :id >= 0)))))
  (let [v (auto [{:id 1 :name "a"}])]
    (is (= [{:id 1 :name "a"}] (vec (lookup v :id 1)))) ; realise the auto index
    (let [v2 (assoc v 0 {:id 1 :name "b"})]
      (is (= [{:id 1 :name "b"}] (vec (lookup v2 :id 1)))))))

(deftest index-survives-emptying-test
  ;; a manually specified index must keep being maintained after the
  ;; collection transiently becomes empty (or all entries for it are removed)
  (let [v (-> (index [1] identity :idx/hash) pop (conj 2))]
    (is (some? (p/-get-eq v identity)))
    (is (= [2] (vec (lookup v identity 2)))))
  (let [m (-> (index {:a 1} identity :idx/unique) (dissoc :a) (assoc :b 2))]
    (is (some? (p/-get-uniq m identity)))
    (is (= 2 (identify m identity 2))))
  (let [s (-> (index #{1} identity :idx/sort) (disj 1) (conj 2))]
    (is (some? (p/-get-sort s identity)))
    (is (= [2] (vec (ascending s identity >= 0))))))

(deftest empty-preserves-indexes-test
  (let [v (index [{:id 1}] :id :idx/unique :id :idx/hash :id :idx/sort)
        e (into (empty v) [{:id 5}])]
    (is (= [{:id 5}] e))
    (is (some? (p/-get-eq e :id)))
    (is (some? (p/-get-uniq e :id)))
    (is (some? (p/-get-sort e :id)))
    (is (sorted? (p/-get-sort e :id)))
    (is (= {:id 5} (identify e :id 5)))
    (is (= [{:id 5}] (vec (lookup e :id 5))))))

(deftest conj-vector-pair-on-map-test
  (let [m (auto {:a {:x 1}})]
    (is (= {:a {:x 1} :b {:x 2}} (conj m [:b {:x 2}])))
    (is (= {:a {:x 1} :b {:x 2}} (into m [[:b {:x 2}]])))
    (is (= [{:x 2}] (vec (lookup (conj m [:b {:x 2}]) :x 2))))
    (is (thrown? #?(:clj IllegalArgumentException :cljs js/Error) (conj m [:b])))))

#?(:clj
   (deftest last-index-of-test
     (let [v (auto [1 2 1])]
       (is (= 2 (.lastIndexOf ^java.util.List v 1)))
       (is (= 0 (.indexOf ^java.util.List v 1))))))

(deftest delete-index-test
  (let [v (index [{:foo 1}] :foo :idx/hash)]
    (is (some? (p/-get-eq v :foo)))
    (let [v2 (delete-index v :foo :idx/hash)]
      (is (nil? (p/-get-eq v2 :foo)))
      ;; queries still work via scanning
      (is (= [{:foo 1}] (vec (lookup v2 :foo 1))))))
  ;; deleting a match index uses the same normalization as creating one
  (let [v (index [{:foo 1 :bar 2}] (match :foo :idx/value :bar :idx/value) :idx/hash)
        v2 (delete-index v (match :foo :idx/value :bar :idx/value) :idx/hash)]
    (is (nil? (p/-get-eq v2 (p/-prop (match :foo 1 :bar 2)))))))

(deftest pcomp-test
  (let [coll [{:a {:b 1}} {:a {:b 2}}]]
    (is (= [{:a {:b 2}}] (vec (lookup coll (pcomp :b :a) 2))))
    (is (= [{:a {:b 2}}] (vec (lookup (auto coll) (pcomp :b :a) 2))))))

(deftest orders-join-use-case-test
  ;; the motivating example from the README: flattening a quadratic join
  (let [orders [{:order-id 1} {:order-id 2} {:order-id 3}]
        items (auto [{:order-id 1 :sku "a"} {:order-id 2 :sku "b"} {:order-id 1 :sku "c"}])
        joined (mapv (fn [o] (assoc o :items (vec (sort-by :sku (lookup items :order-id (:order-id o)))))) orders)]
    (is (= [{:order-id 1 :items [{:order-id 1 :sku "a"} {:order-id 1 :sku "c"}]}
            {:order-id 2 :items [{:order-id 2 :sku "b"}]}
            {:order-id 3 :items []}]
           joined))))

(deftest readme-sort-example-test
  (let [users [{:name "Alice", :age 42}
               {:name "Bob", :age 30}
               {:name "Barbara", :age 12}
               {:name "Jim", :age 83}]]
    (doseq [u [users (auto users) (index users :age :idx/sort)]]
      (is (= ["Alice" "Jim"] (mapv :name (ascending u :age > 30))))
      (is (= ["Bob" "Barbara"] (mapv :name (descending u :age <= 30)))))))

;; properties

(def index-pair
  (gen/elements
    [[identity :idx/hash]
     [identity :idx/unique]
     [hash :idx/sort]]))

;; vectors

(def i-vec-pair
  (gen/bind
    (gen/vector gen/any-printable-equatable 1 100)
    (fn [v]
      (gen/tuple (gen/choose 0 (dec (count v))) (gen/return v)))))

(defn same-vec? [v1 v2]
  (and (= v1 v2)
       (vector? v1)
       (vector? v2)
       (= (peek v1) (peek v2))
       (= (count v1) (count v2))
       (= (first v1) (first v2))
       (= (hash v1) (hash v2))
       (= (str v1) (str v2))
       (= (pr-str v1) (pr-str v2))))

(def vec-props
  {:vec-equality-holds-through-conj
   (prop/for-all [v (gen/vector gen/any-printable-equatable)
                  e gen/any-printable-equatable
                  indexes (gen/vector index-pair)]
     (same-vec? (conj v e) (conj (apply index v (mapcat identity indexes)) e)))


   :vec-equality-holds-through-pop
   (prop/for-all [v (gen/vector gen/any-printable-equatable 1 100)
                  indexes (gen/vector index-pair)]
     (same-vec? (pop v) (pop (apply index v (mapcat identity indexes)))))

   :vec-equality-holds-through-assoc
   (prop/for-all [[i v] i-vec-pair
                  e gen/any-printable-equatable
                  indexes (gen/vector index-pair)]
     (same-vec? (assoc v i e) (assoc (apply index v (mapcat identity indexes)) i e)))

   :vec-identity-lookup-equality
   (prop/for-all [v (gen/vector gen/any-printable-equatable)
                  e gen/any-printable-equatable]
     (let [v (index v identity :idx/unique)
           v (conj v e)]
       (is (= e (identify v identity e)))))

   :vec-hash-membership
   (prop/for-all [v (gen/vector gen/any-printable-equatable)
                  e gen/any-printable-equatable]
     (let [v (index v hash :idx/hash)
           v (conj v e)]
       (is (contains? (set (lookup v hash (hash e))) e))))

   :vec-ascending-same-as-sort
   (prop/for-all [v (gen/vector gen/pos-int)]
     (let [v (index v identity :idx/sort)]
       (is (= (sort v) (ascending v identity >= 0)))))

   :vec-descending-same-as-reverse-sort
   (prop/for-all [v (gen/vector gen/pos-int)]
     (let [v (index v identity :idx/sort)]
       (is (= (reverse (sort v)) (descending v identity >= 0)))))})

;; maps

(def key-map-pair
  (gen/bind
    (gen/map gen/any-printable-equatable gen/any-printable-equatable {:min-elements 1})
    (fn [m]
      (gen/tuple (gen/elements (keys m)) (gen/return m)))))

(defn same-map? [v1 v2]
  (and (= v1 v2)
       (map? v1)
       (map? v2)
       (= (seq v1) (seq v2))
       (= (count v1) (count v2))
       (= (first v1) (first v2))
       (= (hash v1) (hash v2))
       (= (str v1) (str v2))
       (= (pr-str v1) (pr-str v2))))

(def map-props
  {:map-equality-holds-through-conj
   (prop/for-all [m (gen/map gen/any-printable-equatable gen/any-printable-equatable)
                  k gen/any-printable-equatable
                  v gen/any-printable-equatable
                  indexes (gen/vector index-pair)]
     (same-map? (conj m {k v}) (conj (apply index m (mapcat identity indexes)) {k v})))

   :map-equality-holds-through-dissoc
   (prop/for-all [[k m] key-map-pair
                  indexes (gen/vector index-pair)]
     (same-map? (dissoc m k) (dissoc (apply index m (mapcat identity indexes)) k)))

   :map-equality-holds-through-assoc
   (prop/for-all [[k m] key-map-pair
                  e gen/any-printable-equatable
                  indexes (gen/vector index-pair)]
     (same-map? (assoc m k e) (assoc (apply index m (mapcat identity indexes)) k e)))

   :map-identity-lookup-equality
   (prop/for-all [m (gen/map gen/any-printable-equatable gen/any-printable-equatable)
                  e gen/any-printable-equatable]
     (let [m (index m identity :idx/unique)
           m (conj m {e e})]
       (is (= e (identify m identity e)))))

   :map-hash-membership
   (prop/for-all [m (gen/map gen/any-printable-equatable gen/any-printable-equatable)
                  e gen/any-printable-equatable]
     (let [m (index m hash :idx/hash)
           m (conj m {e e})]
       (is (contains? (set (lookup m hash (hash e))) e))))

   :map-ascending-same-as-sort
   (prop/for-all [m (gen/map gen/any-printable-equatable gen/pos-int)]
     (let [m (index m identity :idx/sort)]
       (is (= (sort (vals m)) (ascending m identity >= 0)))))

   :map-descending-same-as-reverse-sort
   (prop/for-all [m (gen/map gen/any-printable-equatable gen/pos-int)]
     (let [m (index m identity :idx/sort)]
       (is (= (reverse (sort (vals m))) (descending m identity >= 0)))))})

(def key-set-pair
  (gen/bind
    (gen/set gen/any-printable-equatable {:min-elements 1})
    (fn [s]
      (gen/tuple (gen/elements s) (gen/return s)))))

(defn same-set? [v1 v2]
  (and (= v1 v2)
       (set? v1)
       (set? v2)
       (= (seq v1) (seq v2))
       (= (count v1) (count v2))
       (= (first v1) (first v2))
       (= (hash v1) (hash v2))
       (= (str v1) (str v2))
       (= (pr-str v1) (pr-str v2))))

(def set-props
  {:set-equality-holds-through-conj
   (prop/for-all [s (gen/set gen/any-printable-equatable)
                  v gen/any-printable-equatable
                  indexes (gen/vector index-pair)]
     (same-set? (conj s v) (conj (apply index s (mapcat identity indexes)) v)))

   :set-equality-holds-through-disj
   (prop/for-all [[k s] key-set-pair
                  indexes (gen/vector index-pair)]
     (same-set? (disj s k) (disj (apply index s (mapcat identity indexes)) k)))

   :set-identity-lookup-equality
   (prop/for-all [s (gen/set gen/any-printable-equatable)
                  e gen/any-printable-equatable]
     (let [m (index s identity :idx/unique)
           m (conj m e)]
       (is (= e (identify m identity e)))))

   :set-hash-membership
   (prop/for-all [s (gen/set gen/any-printable-equatable)
                  e gen/any-printable-equatable]
     (let [s (index s hash :idx/hash)
           s (conj s e)]
       (is (contains? (set (lookup s hash (hash e))) e))))

   :set-ascending-same-as-sort
   (prop/for-all [s (gen/set gen/pos-int)]
     (let [s (index s identity :idx/sort)]
       (is (= (sort s) (ascending s identity >= 0)))))

   :set-descending-same-as-reverse-sort
   (prop/for-all [s (gen/set gen/pos-int)]
     (let [s (index s identity :idx/sort)]
       (is (= (reverse (sort s)) (descending s identity >= 0)))))})

(defn- check-props [props]
  (doseq [[k prop] props]
    (let [result (tc/quick-check 50 prop)]
      (is (true? (:pass? result)) (str k " => " (pr-str (dissoc result :fail :shrunk)))))))

(deftest vec-generative-test (check-props vec-props))
(deftest map-generative-test (check-props map-props))
(deftest set-generative-test (check-props set-props))

(comment
  (clojure.test/run-tests)

  (let [noop-idx true
        ntests 100]

    (with-redefs [index (if noop-idx (fn [c & args] c) index)]
      (doseq [[k prop] (concat vec-props
                               map-props
                               set-props)]
        (println "Testing property" (name k))
        (println "=>")
        (prn (tc/quick-check ntests prop))
        (println ""))))

  )