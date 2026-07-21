# Changelog

## Unreleased

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