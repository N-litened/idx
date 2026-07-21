# Changelog

## Unreleased

- **Breaking**: `:idx/unique` now enforces uniqueness. Building the index throws if the property is not unique across the collection, and any modification that would introduce a duplicate indexed value throws an `ex-info` naming the property, value and both ids (updating the element that owns a value remains fine). This applies to auto-realised unique indexes too: `identify`/`pk`/`replace-by` on an `auto` collection declare the queried property unique. Plain (unwrapped) collections are unaffected — linear scan, first match. Previously duplicates were silently accepted (last-write-wins) and could permanently corrupt the index when either duplicate was later modified.
- Fixed a severe JVM query slowdown (up to ~100x per call) introduced by detecting predicates with `satisfies?`, which caches no negative results on Clojure 1.10; queries now test the protocol's backing interface. JVM `Predicate` implementations must implement the protocol inline (`deftype`/`defrecord`/`reify`), not via `extend`.
- Fixed set `disj` deleting index entries computed from the caller's argument rather than the stored member; `=`-but-property-divergent elements (e.g. metadata-based properties) no longer leave ghost index entries.
- cljs: fixed `disj` of an absent element corrupting unique indexes, and `conj` of an already-present element double-indexing it (the identity-based no-op guards never fire on cljs sets, which always allocate).
- cljs: fixed fractional `assoc` keys on indexed vectors (accepted for host parity) corrupting indexes by recording the fractional key as the element id; out-of-bounds `assoc` now throws exactly the host vector's error again.
- Fixed `(reduce f coll)` without an init value throwing `ClassCastException` on JVM indexed vectors (missing `IReduce`).
- cljs: fixed `reduce`/`into` crashing on indexed maps of more than 8 entries (`IReduce` was delegated to backing maps that don't implement it).
- cljs: fixed vars, `with-meta`'d functions and multimethods used as properties silently matching nothing (they were looked up as keys); multimethods are now invoked on the JVM too.
- cljs: records now wrap as indexed maps like on the JVM, instead of being converted to a vector of map entries.
- cljs: calling an indexed vector as a function now throws on a bad index like the host vector (previously returned nil).
- Invoking a JVM indexed collection with an unsupported arity now throws `ArityException` exactly like the plain collection instead of `AbstractMethodError`.
- JVM indexed collections are now `java.io.Serializable`, like the collections they wrap.
- Wrapping a sorted map/set now throws with an explanatory message instead of returning a wrapper that silently breaks `subseq`/`rseq`/`sorted?`.
- `lookup`/`lookup-keys` misses now return an empty collection on the indexed path, consistent with the unindexed scan path (previously nil).
- `index`/`delete-index` now reject unpaired trailing varargs with a descriptive error, like `match`.
- Docs: `match` docstring described a map argument it does not accept; README `match` example used a regex literal as a value (regex equality is identity, so it could never match); documented that `transient` is unsupported and why.
- Docs: modifying a collection that has an ordering (`:idx/sort`) index — manually requested or realised by an `ascending`/`descending` query on an `auto` collection — with an element whose property value is not comparable with the indexed values is now documented as undefined behaviour (currently it throws, like conj onto a sorted-set; nil values are fine).
- Docs: `:idx/unique` documented as not enforcing uniqueness — duplicate indexed values are silently accepted and are undefined behaviour; `replace-by` documented as defined only for vectors/maps/sets; documented the lazy-query-over-mutable-host-collection quirk and the subvec negative-assoc quirk.
- Added test-runner aliases exiting non-zero on failure: `clojure -M:dev:test-clj` (JVM) and `clojure -M:dev:test-cljs` (ClojureScript on node).
- `(match ...)`/`(pred ...)` forms are now normalised to their underlying property in every property position — the 3-ary query forms (`(lookup coll (pred :a) true)`), `match` keys, and `ascending`/`descending` — matching how `index` already treated them. Previously these silently matched nothing (the predicate object was looked up as a key).
- Fixed valid values colliding with internal `::not-found` sentinels, which could make vector/map updates no-op, leave stale indexes, or make `replace-by` miss map/set elements.
- Fixed JVM indexed-vector `assoc` coercing fractional keys and rejecting large integer keys differently from a persistent vector.
- Map index IDs now retain the actual backing-map key object when an equal, non-identical key is used for an update.
- Unindexed queries over seqs and lists no longer copy the input into a temporary vector before scanning it.
- JVM indexed-map `assocEx` now throws the same exception type as persistent maps.
- Predicate value positions now accept any implementation of the predicate protocol, consistently with the two-argument query forms and manual indexing.
- `match` now rejects an unpaired trailing property instead of silently treating its value as `nil`.
- Fixed `conj` of a `[k v]` vector pair onto an indexed map throwing `IllegalArgumentException` (the pair check was inverted).
- Fixed hash and sorted indexes serving stale elements after `assoc` replaced an element whose indexed property value was unchanged.
- Fixed `index`/`delete-index` given a `match`/`pred` predicate: the underlying property is now indexed. Previously the created index could never be hit by queries.
- Fixed `lookup-keys` throwing when given a predicate in the value position.
- Fixed `pk` returning the element instead of the key when given a predicate in the value position.
- Fixed `identify` returning the element stored under a `nil` map key when the queried value is absent from a unique index.
- Fixed `replace-by` inserting under a `nil` key (maps), throwing (vectors) or conj-ing the replacement (sets) when no element matches; it now returns the collection unchanged.
- Fixed manually-specified indexes silently disappearing (and no longer being maintained) once the last element they covered was removed.
- `empty` on an indexed collection now preserves index definitions (emptied) instead of dropping them.
- Fixed `.lastIndexOf` on indexed vectors returning the first occurrence (clj).
- cljs: fixed `with-meta` on indexed sets never short-circuiting (compared against the `meta` fn), and `nil` used as a property now looks up the `nil` key like clj does.
- Single-property `match` predicates now share the plain property index instead of building a composite one.
- `ascending`/`descending` on collections without a sort index no longer copy the collection into a vector before building the temporary sorted index.

## 0.1.3

- Fixed .cons with maps in sequences of entries using the key as the value! 

## 0.1.2

- Fixed .cons with map entries in clj using the key as the value! 

## 0.1.1

- Fixed `.toArray` overload ambiguity causing library to not be loadable on Java 11.
- Changed some kw `identical?` optimisations to check `=` instead on cljs

## 0.1.0

Initial release
