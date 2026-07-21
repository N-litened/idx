# Changelog

## Unreleased

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
