# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

`idx` is a Clojure(Script) library (published as `com.wotbrew/idx` on Clojars) providing map/set/vector wrappers with secondary indexes. Wrapped collections behave and print exactly like the originals; indexes give sub-linear `lookup`/`identify`/`ascending`/`descending` access and are maintained incrementally through `conj`/`assoc`/`dissoc`/etc.

## Commands

The single test namespace runs on both platforms via `com.wotbrew.idx-test-runner` (exits non-zero on failure). A change touching any impl file must be verified on BOTH platforms:

```sh
# Run the test suite
clojure -M:dev:test-clj    # JVM
clojure -M:dev:test-cljs   # ClojureScript on node (~90s; needs node on PATH)

# Run a single test var
clojure -M:dev -e "(require '[clojure.test :as t] 'com.wotbrew.idx-test) (t/test-vars [#'com.wotbrew.idx-test/indexed-vector-test])"

# REPL with dev deps (criterium, test.check, clojurescript)
clojure -M:dev

# Build jar (depstar -> idx.jar) / deploy to Clojars
clojure -M:jar
clojure -M:deploy
```

Benchmarks live in `bench/com/wotbrew/idx_bench.cljc` (criterium); run forms from a `-M:dev` REPL.

## Architecture

- `src/com/wotbrew/idx.cljc` — the entire public API (`auto`, `index`, `delete-index`, `unwrap`, `lookup`, `identify`, `pk`, `replace-by`, `ascending`, `descending`, and the property combinators `path`, `match`, `pred`, `pcomp`, `as-key`). Everything under `impl/` is explicitly an implementation detail.
- `src/com/wotbrew/idx/impl/protocols.cljc` — the core protocols:
  - `Property` — extracts an index value from an element. Extended in `ext` so functions/vars/keywords are invoked and any other object falls back to `(get element o)`.
  - `Predicate` — a (property, expected-value) pair; how `match`/`pred` compose into composite indexes.
  - `Idx` — index get/add/delete plus element enumeration; `Wrap`/`Unwrap` convert to/from indexed wrappers.
- `src/com/wotbrew/idx/impl/{vector,map,set}.{clj,cljs}` — the `IndexedPersistentVector/Map/Set` deftypes. Each exists twice because the JVM version implements `clojure.lang` interfaces while the CLJS version implements CLJS protocols. **A behavior change in one platform's file usually needs mirroring in the other** (and the same for `ext.clj`/`ext.cljs`).
- `src/com/wotbrew/idx/impl/ext.{clj,cljs}` — extends the protocols onto host types: `Wrap` for plain vectors/maps/sets, `Property` defaults, and `Idx` fallbacks for `nil`/`Object` so every query function also works on plain unindexed collections (via linear scan over `-elements`).
- `src/com/wotbrew/idx/impl/index.cljc` — platform-neutral index construction and incremental maintenance (`add-*`/`del-*` applied on every collection modification).

### Key invariants

- Three index kinds: `:idx/hash` (one-to-many, shape `{property {value {id element}}}`), `:idx/unique` (`{property {value id}}`), `:idx/sort` (same as hash but a `sorted-map`). An element's "id" is its vector index, map key, or the element itself for sets — `identify`/`pk`/`replace-by` resolve ids back through the base collection.
- Each wrapper deftype holds its three index maps in mutable fields. On the JVM they are volatile, and auto-built indexes are published by merging under a lock on the wrapper: construction may race redundantly, but publishing different properties must not lose either declaration (especially because a realised unique index controls later enforcement). Every persistent update constructs a new wrapper with incrementally-updated index maps. CLJS uses ordinary mutable fields because its normal runtime is single-threaded.
- Query functions never require indexes: without one, `lookup`/`identify`/`pk` scan linearly, and `ascending`/`descending` build a throwaway sorted index.
- Wrappers must be indistinguishable from the plain collection (equality, hashing, printing, metadata, all host interfaces including Java's `List`/`Collection` on the JVM). Interop edge cases here have been the source of past bugs (see CHANGELOG: `.toArray` overloads on Java 11, `.cons`/`.assoc` with map entries).
- JVM wrappers implement `Serializable`, but index properties are part of their serialized state and must themselves be serializable (including anything captured by function properties), just like values in the backing collection.
- Two deliberate exceptions to indistinguishability (both throw/refuse rather than silently misbehave — see comments at the relevant sites before "fixing" them): sorted maps/sets are rejected at wrap time (a deftype cannot implement `Sorted`/`Reversible` conditionally), and wrappers are not `IEditableCollection` (a transient could not maintain indexes, and delegating would make `into` silently drop the wrapper).
- Modifying a collection under a `:idx/sort` index (manual, or auto-realised by an `ascending`/`descending` query) with a property value that is not comparable with the indexed values is documented **undefined behaviour** (decided 2026-07: it currently throws the raw comparator error, and stays that way — user-supplied Comparables can throw anything from compareTo, so a wrapping ex-info would promise an error shape we cannot guarantee). nil property values are comparable (sort first) and safe. Duplicate values under `:idx/unique` are ENFORCED against (decided 2026-07, reversing an earlier undefined-behaviour stance): index build and every modification throw ex-info on a violation, including auto-realised unique indexes (`identify`/`pk`/`replace-by` on an auto collection declares the property unique). Plain collections keep first-match linear scans. Enforcement lives in `index.cljc` (`add-uniq`/`create-uniq*`, count-based duplicate detection — no sentinels, ids/values are arbitrary user data).
- Hot-path predicate detection in `idx.cljc` uses `instance?` on the protocol's backing interface on the JVM, not `satisfies?` — `satisfies?` has no negative-result cache on Clojure 1.10 and cost ~100x per query when it was tried.
