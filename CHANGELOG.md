# Changelog

## Unreleased

### Breaking changes

- **`getFullUrl` was replaced by `getFullPathPattern`.**
  `SpaApplicationDefinition` and `SinglePageApplicationConfig` now expose
  `getFullPathPattern(route)`, taking a `SpaRouteDefinition` and returning its
  prefixed path pattern with parameter placeholders intact. The string overload
  was removed. Parameter substitution is handled separately by `resolvePath`.

  Migration: replace `getFullUrl(route)` or `getFullUrl(route.path)` with
  `getFullPathPattern(route)`, and `getFullUrl(route.resolvePath(values))` with
  `route.resolvePath(getFullPathPattern(route), values)`. On
  `SinglePageApplicationConfig`, `getFullUrls()` was renamed to
  `getFullPathPatterns()`. Query-string handling is unchanged.

- **Regenerate server routes after upgrading.** `SpaTypedRoute` no longer
  supplies an inherited zero-argument `invoke()`. The generator now emits each
  route's invocation method, so required path and query arguments cannot be
  bypassed by calling the route without arguments. Routes without required
  arguments remain callable with no arguments after regeneration. Custom
  subclasses that need a zero-argument call must define it explicitly.

- **Route parameters now support strings only.** The `int()` and `uuid()`
  factories, `SpaRouteParameterType`, `SpaRouteParameter.type`, and
  `SpaRouteParameter.hasValidValue()` were removed. Parameter values use
  Kotlin `String` and TypeScript `string`; repeated query parameters use lists
  of strings. Optional parameters remain supported.

  The runtime still rejects missing required parameters and unknown parameter
  names, but no longer validates integer or UUID formats. Any value-format
  validation belongs in application code.

  Migration: replace `int("id")` and `uuid("id")` with `string("id")` and
  update the imports. For direct construction, use `SpaRouteParameter("id")`
  without a type argument.
  Regenerate client and server routes, then pass string values to route builders
  (for example, `UserDetail(id = "123")` in Kotlin or
  `UserDetail({ id: "123" })` in TypeScript). Explicit parameter metadata is
  still required.

### Added

- Strongly typed query declarations via `queryParameters = listOf(string("q"),
  string("tag").repeated().optional())`. Generated Kotlin `Query` data classes
  and TypeScript query types support required/optional strings and lists, with
  a separate query argument on route builders. Repeated lists produce repeated
  URL keys; values and names are encoded as UTF-8 form query strings.
- Generated route-specific query enums and `queryParameters(...)` helpers expose
  declared values through enum-keyed maps. Raw incoming query maps retain extra
  keys. Query values remain strings; the helpers preserve lists without parsing
  them into the generated `Query` model.
- Declared query cardinality is validated before rules for page loads and route
  decisions, and when resolving typed redirects. Required scalars need one value,
  optional scalars accept at most one, and required lists need at least one.
  Empty strings and extra incoming keys are allowed. Routes without query
  declarations preserve their unrestricted query handling.
- `spa-routing.server.invalid-query-parameter-status` configures invalid-query
  responses independently of path parameters and defaults to `400`.

### Fixed

- Client URL generation now substitutes complete path parameter segments, so
  names such as `query` and `query_` do not interfere with each other.
- Route declarations reject names that collide after generated identifier
  normalization, including query enum names.

## 0.3.0 (2026-07-18)

### Breaking changes

- **The core contract package `com.caseymcguiredotcom.sparoutecontract` was
  renamed to `com.sparouting.contract`** (its `codegen` subpackage moved with
  it). Two defaults changed along with it:

  - The default generated server routes package is now
    `com.sparouting.generated.spa.routes` (was
    `com.caseymcguiredotcom.generated.spa.routes`).
  - The plugin's default route-definitions source directory is now
    `src/main/kotlin/com/sparouting/contract/applications` (was
    `src/main/kotlin/com/caseymcguiredotcom/sparoutecontract/applications`).

  Migration: update imports of contract types (`SpaApplicationDefinition`,
  `SpaRouteKey`, `route`, `int`, ...) to `com.sparouting.contract`, move your
  route definition files to the new default directory (or set
  `routeDefinitions.sourceDirectory` explicitly), and regenerate server routes.
  Maven coordinates (`io.github.caseymcguire:*`) are unchanged.

## 0.2.0 (2026-07-16)

### Breaking changes

- **SPA route rules now evaluate in two stages with different fallthrough
  defaults.** Previously, application-wide rules and route-level rules were
  concatenated into a single chain that was allow-by-default: if every rule
  returned `Skip` — or no rules were configured — the route was served.

  Now:

  - Application-wide rules (`SinglePageApplicationConfig.rules`) are a
    **gate, deny-by-default**: the first `Allow` passes the request on to the
    route-level rules, the first `Deny` denies it, and if every rule skips —
    including when the SPA has no rules at all — the request is answered
    with `404`.
  - Route-level rules (`SinglePageApplicationConfig.routeRules`) are
    **vetoes, allow-by-default**: the first `Deny` denies the request, the
    first `Allow` serves it, and if every rule skips the route is served.
  - An application-level `Allow` passes the gate but no longer short-circuits
    route-level rules — those always run once the gate passes.

  Migration:

  - An SPA with no `rules` configured now answers `404` on every route. Add
    `rules = listOf(AllowAll())` (new builtin, see below) to keep serving it.
  - Gate rules that returned `Skip` on success (e.g. a `RequireLogin` that
    denies anonymous users and otherwise skips) should return `Allow` on
    success, or be followed by `AllowAll()` in the chain.
  - `SpaRouteResponseEvaluator.evaluate(rules, request)` is now
    `evaluate(applicationRules, routeRules, request)`. Callers that
    concatenated the two chains themselves must pass them separately.

  The route decision endpoint (`/__spa/route-decision`) and the generated
  client `canNavigate` guard reflect the same verdicts automatically.

- **`generateWebpackBundleEntries` was replaced by the bundler-neutral
  `generateBundleEntries` task.** The `webpackBundleEntries { ... }`
  configuration block and the `webpackBundleEntriesOutputFile` extension
  property were likewise renamed to `bundleEntries { ... }` and
  `bundleEntriesOutputFile`. The generated file's contents are unchanged
  (only its regeneration-command comment differs), so migration is a rename
  in `build.gradle.kts` and in any scripts or CI steps invoking the task.

### Added

- Built-in rules in `io.github.caseymcguire.sparouting.spring.rules.builtin`:
  - `AllowAll` — allows every route; the explicit opt-in for an ungated SPA.
  - `AllowPublic(vararg routes: SpaRouteKey)` — allows the listed routes for
    everyone; place it ahead of an application-wide gate to exempt public
    routes while everything else stays gated.
  - `DenyAll(action)` — denies every route (404 by default); an
    application-wide kill switch or a route-level veto for routes taken out
    of service.
- `generateBundleEntries` Gradle task and `bundleEntries { outputFile = ... }`
  configuration. The generated bundle-name-to-app-root-path map is
  bundler-neutral: webpack consumes it as `entry`, Vite as
  `build.rollupOptions.input` (see `docs/spring-boot-client-apps.md` for the
  Vite setup).
