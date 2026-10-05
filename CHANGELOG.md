# Changelog

## Unreleased

### Breaking changes

- **Access handlers are registered independently of application configs.**
  `ApplicationAccessHandler` is now an abstract class taking the protected
  `SinglePageApplicationDefinition` in its constructor. Extend it with, for
  example, `CheckBlogAccess : ApplicationAccessHandler(BlogApplication)`.
  `SinglePageApplicationConfig.accessHandler` is removed. Register application
  handlers as standalone beans, just like route handlers; the previous lambda
  interface and config wiring are no longer supported.

  `SinglePageApplicationRouteRegistry` now takes configs, application handlers,
  and optional route handlers. It validates exactly one application handler per
  configured application, rejects unknown application handlers, and owns the
  existing route handler validation. Registrations now include a required
  `applicationAccessHandler` and optional `routeAccessHandler`.
  `RouteAccessEvaluator` takes only the registry, retrieves both handlers in one
  lookup, and exposes `evaluate(request)`. Update custom wiring and evaluator
  overrides; callers no longer pass handlers to the evaluator. Spring collects
  and injects both handler lists into the registry automatically.

- **Application rule lists are replaced by one required application access handler.**
  Extend `runtime.access.ApplicationAccessHandler(ApplicationDefinition)` and
  implement `evaluate(RouteRequest)`. Register one implementation per application.
  Public applications must explicitly return `AccessDecision.Allow`.

  Access has two levels: the application handler runs first, then the matching
  typed route handler if the application returns `Allow`. An application
  redirect short-circuits the route handler. Unflagged routes are allowed after
  the application check succeeds. Request validation still precedes both checks.

  Both handlers return `com.sparouting.contract.AccessDecision`, renamed from
  `RouteDecision`. Its outcomes are `Allow` and `Redirect(RouteTarget)`.
  `SinglePageApplicationConfig.rules`, `RouteRule`, `RouteRuleResult`,
  `RouteRuleAction`, `RouteRuleActionResolver`, and the `AllowAll`, `AllowPublic`,
  and `DenyAll` builtins are removed, with no aliases. Compose reusable checks
  inside handlers. Ordered rule evaluation, `Skip`, raw URL redirects, and
  custom HTTP status denials are no longer part of the access API. Replace
  denied outcomes with typed redirects to registered routes. Validation's
  configured HTTP status responses are unchanged.

  Update imports and handler return types, then recompile consumers.
  `RouteAccessEvaluator.evaluate(request)` selects the application handler from
  its injected registry and returns `AccessDecision`. Callers no longer supply
  a handler. `RouteResponseService` resolves typed redirects itself and
  no longer takes an action resolver; the `routeRuleActionResolver` Spring bean
  is removed. Update custom runtime wiring accordingly.

- **Access evaluation is consolidated in `runtime.access.RouteAccessEvaluator`.**
  It replaces `RouteResponseEvaluator` and `RouteHandlerRegistry`, taking only
  the route registry. The registry validates handler registrations at construction;
  the evaluator executes the application handler before the matching route handler.
  Its `evaluate(request)` method returns
  `AccessDecision` instead of `RouteHttpResponse`. Redirect decisions retain
  their unresolved targets.

  `RouteResponseService` now takes `accessEvaluator` along
  with the route registry. It validates requests and converts access results
  into response data, including resolving redirects. The Spring bean is now
  `routeAccessEvaluator`; the separate `routeResponseEvaluator` and
  `routeHandlerRegistry` beans are removed. Update custom wiring and overrides.
  No aliases are provided. Handler registration validation is unchanged.

- **Shared server logic now lives in `spa-routing-runtime`.** Update imports
  from `io.github.caseymcguire.sparouting.spring` to
  `io.github.caseymcguire.sparouting.runtime` for `config.*` (including
  `SinglePageApplicationConfig`), access evaluation,
  `request.RouteRequest`, and the response models and `RouteResponseService`.
  The Spring starter includes the new module transitively. No aliases for the
  old packages are provided.

  `SinglePageApplicationConfig` no longer exposes `renderHtml()`. Configurations
  overriding that method must implement
  `spring.config.SpringSinglePageApplicationConfig`. Spring request factories,
  `HtmlRenderer`, response conversion extensions, router factories, and
  auto-configuration remain in their Spring packages. Java callers of response
  extensions now use `RouteResponsesKt` instead of `RouteHttpResponseKt`.

  `RouterFunctionFactory` now takes a `RouteResponseService` instead of a
  `RouteResponseEvaluator` and `RoutingProperties`. Both page serving and
  navigation decisions use the service's shared validation and evaluation
  pipeline. Custom services should account for both `evaluate(RouteRequest)`
  and `evaluate(RouteResponseRequest)`. A custom `RouteRequestFactory` now runs
  before validation, which checks its returned values.

  The runtime also supplies `HtmlDocumentRenderer` and `HtmlRenderingOptions`
  without HTTP framework types. Evaluation remains synchronous; a Ktor adapter
  is not included. See [the runtime guide](docs/runtime.md).

- **The remaining API types and helpers drop the `Spa` prefix.** Update imports
  and usages, then regenerate routes and recompile consumers. Apply the runtime
  package migration above as well; no aliases for the old names are provided.

  | Previous name | New name |
  | --- | --- |
  | `SpaRouteParameter` | `RouteParameter` |
  | `SpaRouteResponseEvaluator` | `RouteAccessEvaluator` |
  | `SpaRouteHttpResponse` | `RouteHttpResponse` |
  | `SpaRouteResponseRequest` | `RouteResponseRequest` |
  | `SpaRouteResponseService` | `RouteResponseService` |
  | `SpaRouteRequestFactory` | `RouteRequestFactory` |
  | `DefaultSpaRouteRequestFactory` | `DefaultRouteRequestFactory` |
  | `SpaHtmlRenderer` | `HtmlRenderer` |
  | `DefaultSpaHtmlRenderer` | `DefaultHtmlRenderer` |
  | `SpaRouterFunctionFactory` | `RouterFunctionFactory` |
  | `SpaRouteDecisionRouterFunctionFactory` | `RouteDecisionRouterFunctionFactory` |
  | `SpaRoutingAutoConfiguration` | `RoutingAutoConfiguration` |
  | `SpaRoutingProperties` | `RoutingProperties` |
  | `SpaRoutingPlugin` | `RoutingPlugin` |
  | `SpaRoutingExtension` | `RoutingExtension` |
  | `SpaRoutingConfiguration` | `RoutingConfiguration` |

  Spring bean names also drop the prefix: for example, `spaHtmlRenderer` becomes
  `htmlRenderer`, and `spaRouteDecisionRouterFunction` becomes
  `routeDecisionRouterFunction`. Update any bean-name references or overrides.
  Java callers of the top-level factories now use `RouteFactoriesKt` instead
  of `SpaRouteFactoriesKt`; Kotlin `route` and `parameter` imports stay the same.

  The Gradle task `generateServerSpaRoutes` is now `generateServerRoutes`.
  Update task references and scripts. Generated TypeScript uses the internal
  helper name `RouteMetadata`. Artifact IDs, plugin ID, `spaRouting` configuration,
  generated source paths, `spa-routing.*` properties, and the
  `/__spa/route-decision` endpoint remain unchanged. Routing behavior is unchanged.

- **Per-route rule lists were replaced by typed access handlers.**
  `SinglePageApplicationConfig.routeRules` and `getRouteRules` were removed.
  Set `generateAccessHandler = true` on routes that need checks, then register
  an implementation of the generated `<Route>AccessHandler`. Return
  `AccessDecision.Allow` to serve the requested route or `AccessDecision.Redirect`
  to send the user to another route; reusable checks can be composed inside
  the handler. Regenerate server routes after opting in.

  The required application access handler must allow the request before its
  route handler runs. Unflagged routes are served after that check passes.

- **`SpaApplicationDefinition` was renamed to `SinglePageApplicationDefinition`.**
  Update imports and implemented interfaces in shared route definitions. The
  discovery and validator helpers are now `SinglePageApplicationDefinitionDiscovery`
  and `SinglePageApplicationDefinitionValidator`. All three remain in
  `com.sparouting.contract`. Regenerate routes and recompile consumers after
  updating; the old type names are removed.

- **The public `RouteKey` interface was removed.** `RouteAccessHandler.route`
  now accepts `Route`
  directly. Replace `RouteKey` (or `SpaRouteKey`) type declarations with `Route`.
  Custom implementations should extend `Route(applicationId, routeId)` or use
  a `Route` instance directly. Generated route objects already extend `Route`.
  Runtime matching continues to use application and route IDs.

- **The following route API types drop the `Spa` prefix.** Rename imports and usages:

  | Previous name | New name |
  | --- | --- |
  | `SpaRouteAccessContext` | `RouteAccessContext` |
  | `SpaRouteTarget` | `RouteTarget` |
  | `SpaRouteDefinition` | `RouteDefinition` |
  | `SpaRouteRequest` | `RouteRequest` |
  | `SpaTypedRoute` | `Route` |

  With the runtime extraction above, `RouteRequest` is now in
  `io.github.caseymcguire.sparouting.runtime.request`; the other types are in
  `com.sparouting.contract`. Regenerate server routes with
  `./gradlew generateServerRoutes`, then recompile consumers. Custom route
  subclasses now extend `Route`. This is a source and binary API rename;
  no aliases for the previous names are provided. Routing behavior is unchanged.

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
  `SinglePageApplicationDefinition` and `SinglePageApplicationConfig` now expose
  `getFullPathPattern(route)`, taking a `RouteDefinition` and returning its
  prefixed path pattern with parameter placeholders intact. The string overload
  was removed. Parameter substitution is handled separately by `resolvePath`.

  Migration: replace `getFullUrl(route)` or `getFullUrl(route.path)` with
  `getFullPathPattern(route)`, and `getFullUrl(route.resolvePath(values))` with
  `route.resolvePath(getFullPathPattern(route), values)`. On
  `SinglePageApplicationConfig`, `getFullUrls()` was renamed to
  `getFullPathPatterns()`. Query-string handling is unchanged.

- **Regenerate server routes after upgrading.** `Route` no longer
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
  construction, use `RouteParameter("id")` without a type argument.
  Regenerate client and server routes, then pass string values to route builders
  (for example, `UserDetail(id = "123")` in Kotlin or
  `UserDetail({ id: "123" })` in TypeScript).

### Added

- Generated client route builders expose `hasAccessHandler: boolean`, derived
  from `generateAccessHandler`. The flag is present on routes with and without
  path or query-string parameters. Regenerate client routes to expose it.
  It describes route-specific checks; application-wide access checks remain separate.
  The public blog example uses the flag to call the decision endpoint only for
  `Post` and `EditPost`, while retaining parameter validation for every route.

- Routes can opt into generated access handlers with `generateAccessHandler = true`.
  Server generation adds sibling `<Route>Request` models and `<Route>AccessHandler`
  abstract classes extending the framework-neutral `RouteAccessHandler<R>` base.
  Requests expose typed path and declared query-string values, plus raw request
  metadata through `context`. Handlers return `AccessDecision.Allow` or a typed
  `AccessDecision.Redirect`.
- Spring automatically registers access-handler beans and fails startup for
  missing, duplicate, unknown-route, or unflagged-route handlers. Validated page
  requests and client route decisions run the same handler after the application
  gate. Unflagged routes are served once the application gate passes;
  the flag does not disable application access checks or client checks.
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
- Declared query cardinality is validated before access checks for page loads and route
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
