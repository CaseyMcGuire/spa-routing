# Changelog

## Unreleased

### Breaking changes

- **`string(name)` was renamed to `parameter(name)`.** All values are strings;
  the declaration specifies the name and optional/repeated behavior. Update
  factory calls and imports. Required path parameters are now inferred from
  placeholders and need no declaration.

- **The query-string API now uses `queryString` consistently.** Rename
  `queryParameters` declarations, request/target properties, and generated map
  helpers to `queryString`. Generated Kotlin builders now take `queryString =`
  with a nested `QueryString` model; TypeScript types use the `QueryString`
  suffix. Enums are now `QueryStringKey` / `<Route>QueryStringKey`. Client
  parsers return `{ params, queryString }` instead of `{ params, query }`.

  Regenerate both client and server routes, then update callers. Rename
  `queryParameter(name)` to `queryStringValue(name)` and
  `hasValidQueryParameterValues(...)` to `hasValidQueryStringValues(...)`.
  Route-decision requests now use the `queryString.` prefix instead of
  `queryParameters.`, and the invalid-value setting is now
  `spa-routing.server.invalid-query-string-status` (previously
  `invalid-query-parameter-status`). Types, encoding, and validation behavior
  are unchanged.

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

  Migration: omit redundant path declarations and infer names from the path.
  For query-string declarations or explicit path overrides, replace `int("id")`
  and `uuid("id")` with `parameter("id")` and update the imports. For direct
  construction, use `SpaRouteParameter("id")` without a type argument.
  Regenerate client and server routes, then pass string values to route builders
  (for example, `UserDetail(id = "123")` in Kotlin or
  `UserDetail({ id: "123" })` in TypeScript).

### Added

- Routes can opt into generated access handlers with `generateAccessHandler = true`.
  Server generation adds sibling `<Route>Request` models and `<Route>AccessHandler`
  abstract classes extending the framework-neutral `RouteAccessHandler<R>` base.
  Requests expose typed path and declared query-string values, plus raw request
  metadata through `context`. Handlers return `RouteDecision.Allow` or a typed
  `RouteDecision.Redirect`.
- Spring automatically registers access-handler beans and fails startup for
  missing, duplicate, unknown-route, or unflagged-route handlers. Validated page
  requests and client route decisions run the same handler after the application
  gate and before existing route rules. Unflagged routes retain their existing
  rule behavior; the flag does not disable application rules or client checks.
- Path parameters are inferred as strings from route placeholders, so
  `route("users/{id}", "UserDetail")` needs no separate parameter declaration.
  Explicit metadata remains supported for optional path values. Duplicate and
  colliding inferred names are rejected before code generation.
- Generated TypeScript route objects expose `parse(params, searchParams)`,
  returning typed path and query objects or `null` for invalid declared values.
  Consumer router adapters can infer renderer arguments from its return type.
- Strongly typed query-string declarations via `queryString = listOf(parameter("q"),
  parameter("tag").repeated().optional())`. Generated Kotlin `QueryString` data classes
  and TypeScript query types support required/optional strings and lists, with
  a separate query argument on route builders. Repeated lists produce repeated
  URL keys; values and names are encoded as UTF-8 form query strings.
- Generated route-specific query-string enums and `queryString(...)` helpers expose
  declared values through enum-keyed maps. Raw incoming query maps retain extra
  keys. Query values remain strings; the helpers preserve lists without parsing
  them into the generated `QueryString` model.
- Declared query cardinality is validated before rules for page loads and route
  decisions, and when resolving typed redirects. Required scalars need one value,
  optional scalars accept at most one, and required lists need at least one.
  Empty strings and extra incoming keys are allowed. Routes without query
  declarations preserve their unrestricted query handling.
- `spa-routing.server.invalid-query-string-status` configures invalid-query-string
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
