# Changelog

## Unreleased

### Breaking changes

- **Navigation responses carry semantic outcomes and recovery destinations.** The
  decision endpoint now serializes `RouteResult` as `{ "type": "allowed" }` or a
  failure with `type` (`denied`, `unknown_route`, or `invalid_request`) and required
  `destination`. Embedded `statusCode`, `location`, and `reason` fields are removed.
  The HTTP envelope remains `200` with
  `Cache-Control: no-store`. Clients must handle the new discriminator and follow
  the destination for every failure.

  `UnknownRoute` and `InvalidRequest` are now data classes carrying
  destinations; `Denied.destinationUrl` is renamed to `destination`. All three
  implement `RouteResult.Failure`. `AccessDecision.Denied` now takes only a typed
  `destination`, and `DenialReason` is removed. Remove reason arguments from access
  handlers and failure handlers; destination pages own user-facing explanations.

  `RouteRequestEvaluator` requires a framework-neutral `RouteFailureHandler`,
  supplied as a bean to Spring or as Ktor's `failureHandler` argument. Its
  `unknownRoute(request)` and `invalidRequest(request)` methods return
  `AccessDecision.Denied` with typed recovery targets. `DefaultRouteFailureHandler`
  accepts explicit `unknownRouteDestination` and `invalidRequestDestination`
  targets. The evaluator validates and resolves those targets for both entry
  points, preserving the failure type. Unknown application IDs use
  the same handler; native framework URL misses retain framework behavior.

  HTTP conversion now applies only to pages. The default converter redirects
  every failure to its destination with `302`; `RouteHttpResponse` contains only
  `statusCode` and `location`. `invalidRequestStatus` and Spring's
  `spa-routing.server.invalid-request-status` are removed. Configure recovery
  in the shared failure handler and page transport through the HTTP converter.

  The blog examples configure Not Found and Error recovery routes. Their frontend
  uses middleware for the new JSON format because `@spa-kit/react-router` 0.2.0's
  built-in authorization helper expects the previous HTTP-shaped payload.

## 0.4.0 (2026-10-06)

### Breaking changes

- **Route lookup and request validation have distinct, simpler results.**
  `RouteResult.UnknownRoute` replaces `NotFound` and means the requested application
  or route ID is not registered. `RouteResult.InvalidRequest` replaces both
  `InvalidPathParameters` and `InvalidQueryString`. It covers path or declared query
  parameters that do not satisfy a known route's contract; validation still precedes
  both access checks. Resource existence remains an application concern.

  `DefaultRouteHttpResponseConverter` maps `UnknownRoute` to `404` and `InvalidRequest`
  to one configurable status, default `400`. Replace the separate path/query status
  settings with `invalidRequestStatus` in the default converter and Ktor registration,
  or `spa-routing.server.invalid-request-status` in Spring. Update custom converter
  branches to use the new results. The former result names and settings are removed.

- **HTTP conversion is shared and replaceable.** Both adapters use the framework-neutral
  `runtime.response.RouteHttpResponseConverter.convert(RouteRequest, RouteResult)`.
  Its `DefaultRouteHttpResponseConverter` preserves the existing mappings and
  validation status settings. Supply a Spring converter bean or Ktor's
  `responseConverter` argument to customize status, location, and reason for both
  page requests and navigation checks, including invalid and unknown routes.
  An explicit converter owns its configuration; the existing Spring properties
  and Ktor status arguments configure only the default converter.

  The converter returns `RouteHttpResponse(statusCode, location, reason)`, replacing
  `RouteDecisionResponse`. Navigation JSON retains those fields, its HTTP `200`
  envelope, and `Cache-Control: no-store`. HTML renders only for an `Allowed` result
  mapped to `200` without a location; custom redirects and errors suppress rendering.
  Evaluation still returns `RouteResult`, independently of HTTP conversion.

  Spring router factories now require `responseConverter`; the page factory no
  longer accepts routing properties. `toServerResponse` now receives converted
  `RouteHttpResponse` metadata, the original result, and the application config.
  Generated application configs and access-handler contracts are unchanged.

- **Access decisions describe navigation permission and include denial reasons.**
  `AccessDecision.Allowed` replaces `Allow`. `AccessDecision.Denied(reason, destination)`
  replaces `Redirect(destination)` and requires a core `DenialReason(code, message)`
  alongside the typed `RouteTarget`. Applications define both the reason code and
  user-facing message. Update application and route handlers; no compatibility
  aliases are provided.

  The evaluator returns `RouteResult.Denied(reason, destinationUrl)` instead of
  `RouteResult.Redirect(url)`, preserving the reason while validating and resolving
  the destination. Application denials still stop route access evaluation.
  Spring and Ktor map denied page requests to HTTP `302` responses. Navigation JSON
  retains `statusCode` and `location` and adds a nullable `reason` object with `code`
  and `message`; it is populated for denials. Page redirects do not transport the
  reason to the destination page. Validation and lookup failure results and their
  configured statuses are unchanged.

- **Route evaluation returns semantic results; adapters own HTTP mapping.**
  `runtime.evaluation.RouteRequestEvaluator(configs)` replaces `RouteResponseService`
  and the internal `RouteAccessEvaluator`. It validates the request, runs application
  access before route access, and resolves redirects using the same registration.
  `evaluate(RouteRequest)` returns `RouteResult.Allowed`, `Redirect(url)`, `NotFound`,
  `InvalidPathParameters`, or `InvalidQueryString`.

  The evaluator takes no HTTP status settings. Spring and Ktor retain their existing
  validation-status configuration and map every result to an explicit response.
  Only `Allowed` renders HTML; configuring a validation status as `200` no longer
  renders an invalid page. Spring's `toServerResponse` now takes a `RouteResult`,
  application config, and routing properties and always returns a complete response.
  Router factories take `evaluator` instead of a response service; the page factory
  also takes routing properties for validation-status mapping.

  `RouteHttpResponse` is replaced by `runtime.response.RouteDecisionResponse`, used
  only as the navigation JSON payload. Its `{ statusCode, location }` wire format,
  HTTP `200` envelope, and `Cache-Control: no-store` are unchanged. Existing failure
  status settings remain in use.

- **HTML rendering belongs to each application config.** `SinglePageApplicationConfig`
  and generated config constructors now require `htmlRenderer: com.sparouting.contract.HtmlRenderer`.
  Its `render(application)` method returns an HTML string. The runtime's
  `HtmlDocumentRenderer` implements this interface; generated code still depends
  only on core. Regenerate server sources and supply a renderer when constructing configs.

  Spring and Ktor use the config's renderer after an allowed page request.
  Navigation decisions, redirects, and invalid requests do not invoke it.
  Ktor's separate `htmlRenderer` argument is removed. Spring's `HtmlRenderer`,
  `DefaultHtmlRenderer`, `SpringSinglePageApplicationConfig`, and automatic renderer
  bean are removed. Pass a core renderer into the config instead of overriding
  `renderHtml()` or replacing the Spring rendering bean. Adapters own the HTTP response.

  `spa-routing.assets.*` properties and `HtmlRenderingOptions` are removed.
  Pass `bundleBasePath`, `includeRouteStylesheet`, and `globalStylesheet` directly
  to `HtmlDocumentRenderer` to configure bundle paths and stylesheets. Defaults
  are unchanged.

- **`RouteResponseService` now owns runtime assembly.** Construct it with
  `configs: List<SinglePageApplicationConfig>` and optional validation status
  codes. It builds and validates one registry and access evaluator at construction.
  The service and its `evaluate` method are final; the registry, registration,
  and evaluator types are internal. Customize access through application and
  route handlers instead of replacing runtime components.

  Spring creates one private service for both page routes and navigation checks.
  The `singlePageApplicationRouteRegistry`, `routeAccessEvaluator`, and
  `routeResponseService` beans are removed. Existing rendering, request conversion,
  endpoint, and validation-status settings remain available. Custom endpoints
  can construct their own service from configs.

  Ktor's `singlePageApplicationRoutes` now accepts configs and an HTML renderer
  and constructs the service internally. Remove its `responseService` argument;
  use `invalidPathParameterStatus` and `invalidQueryStringStatus` to customize
  validation responses (both default to `400`).

- **Shared application contracts now live in core.** Move imports for
  `SinglePageApplicationConfig` from `com.sparouting.runtime.config`,
  `ApplicationAccessHandler` and `RouteAccessHandlers` from
  `com.sparouting.runtime.access`, and `RouteRequest` from
  `com.sparouting.runtime.request` to `com.sparouting.contract`.
  No compatibility aliases are provided; regenerate sources and recompile consumers.

  Generated server code requires only `spa-routing-core`. The generator derives
  config and handler contract imports from class references, and core's tests
  no longer depend on runtime. `spa-routing-runtime` continues to depend on core
  and owns registration, validation, evaluation, redirect resolution, and rendering.
  The concrete generated application config and its constructor API are unchanged.

- **Generated application configs own their typed access handlers.**
  `generateServerRoutes` now emits a concrete `<ApplicationName>ApplicationConfig`,
  `<ApplicationName>ApplicationAccessHandler`, and `<ApplicationName>RouteAccessHandlers`.
  For example, construct `BlogApplicationConfig(applicationAccessHandler, routeAccessHandlers)`
  directly. It implements `SinglePageApplicationConfig` and contains generated
  `id`, `name`, `bundleName`, and `routes` metadata. No config subclass is needed.
  `SinglePageApplicationManifest`, generated `<ApplicationName>Manifest` classes,
  and `SinglePageApplicationConfig.manifest` are removed.

  `ApplicationAccessHandler<C : SinglePageApplicationConfig>` no longer takes
  a manifest instance. Generated application handlers bind `C` to their generated
  config type. The new `RouteAccessHandlers<C>` contract exposes the runtime handler
  list; its generated implementation requires one correctly typed constructor
  argument per gated route. Missing or mismatched handlers fail compilation.
  Apps without gated routes get a zero-argument collection.

  `SinglePageApplicationRouteRegistry` now takes only configs and reads their
  handlers. Spring discovers config beans instead of collecting global handler
  lists. Update bean wiring to construct the generated collection and config.
  Runtime validation still checks missing, duplicate, unknown-route, unflagged,
  and wrong-application route handlers for custom config implementations.
  Generated server configs and authoring projects require only `spa-routing-core`.
  The runtime handles route registration and evaluation and is included by the
  Spring starter. Generated code has no framework annotations. For custom Spring rendering, delegate
  `SinglePageApplicationConfig` to a generated instance while implementing
  `SpringSinglePageApplicationConfig`.

  Regenerate sources and recompile consumers. Request validation, the application
  check followed by the route check, client metadata, and redirects are unchanged.

- **Page requests and navigation checks now share `RouteRequest`.**
  `RouteResponseRequest` and its `RouteResponseService.evaluate` overload are
  removed. Use `com.sparouting.contract.RouteRequest` with application
  and route IDs, `pathParameters`, `queryString`, and actual request headers.
  Rename `parameters` to `pathParameters` when migrating navigation checks.
  Custom response services now override just `evaluate(RouteRequest)`.

  `RouteRequest.method` and `.path` are removed. Typed handlers still receive
  `RouteAccessContext`, with `GET` and the destination path resolved from the
  route manifest for both entry points, rather than the raw page-request path.
  Update request factories and application handlers that used the removed fields.
  Header forwarding, validation, access-check order, and redirect handling are
  unchanged. The decision endpoint still accepts `parameters.*` query keys and
  returns the same JSON response.

- **Publishing coordinates retain the existing namespace.** Maven artifacts use
  `io.github.caseymcguire:<artifact>` and the Gradle plugin ID remains
  `io.github.caseymcguire.spa-routing`. Kotlin packages use `com.sparouting`
  independently of these publishing coordinates.

- **All Kotlin packages now use `com.sparouting`.** Update imports from
  `io.github.caseymcguire.sparouting.runtime`, `.spring`, and `.gradle` to
  `com.sparouting.runtime`, `.spring`, and `.gradle`. Core contracts remain in
  `com.sparouting.contract`. Spring auto-configuration metadata and the Gradle
  plugin implementation class use the new packages. Recompile consumers;
  the former packages have no compatibility aliases.

- **`RouterFunctionFactory` is renamed to `SpringRouterFunctionFactory`.**
  Update imports and constructor calls in `com.sparouting.spring.web`.
  Routing behavior is unchanged.

- **Runtime configuration uses generated metadata instead of authoring definitions.**
  `SinglePageApplicationConfig.routes` contains `RouteManifest` values with full
  path patterns, parameter metadata, and `hasAccessHandler` flags. Configs expose
  `id`, `name`, and `bundleName` directly; the former `applicationId` is now `id`.
  Runtime configs no longer expose `urlPrefix`, `appRootPath`,
  `getFullPathPattern`, or `getFullPathPatterns`; use `route.path` and
  `route.resolvePath(parameters)`. Registrations and request factories take
  `RouteManifest`, not `RouteDefinition`.

  Keep `SinglePageApplicationDefinition` and `RouteDefinition` on the generator
  classpath. The definitions project is no longer an implementation dependency
  of the server; configure it through `routeDefinitions.projectPath` or a dedicated
  generator classpath. The Spring blog runs without `examples:route-definitions`.

- **Application rule lists are replaced by one required application access handler.**
  Extend the generated `<ApplicationName>ApplicationAccessHandler` and
  implement `evaluate(RouteRequest)`. Supply it to the generated application config.
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
  from `com.sparouting.spring` to
  `com.sparouting.runtime` for configuration validation, route registration,
  access evaluation, and the response models and `RouteResponseService`.
  Shared config, request, and handler contracts are in `com.sparouting.contract`.
  The Spring starter includes the new module transitively. No aliases for the
  old packages are provided.

  `SinglePageApplicationConfig` no longer exposes `renderHtml()`. Configurations
  overriding that method must implement
  `spring.config.SpringSinglePageApplicationConfig`. Spring request factories,
  `HtmlRenderer`, response conversion extensions, router factories, and
  auto-configuration remain in their Spring packages. Java callers of response
  extensions now use `RouteResponsesKt` instead of `RouteHttpResponseKt`.

  `SpringRouterFunctionFactory` now takes a `RouteResponseService` instead of a
  `RouteResponseEvaluator` and `RoutingProperties`. Both page serving and
  navigation decisions use the service's shared validation and evaluation
  pipeline through `evaluate(RouteRequest)`. A custom `RouteRequestFactory` now runs
  before validation, which checks its returned values.

  The runtime also supplies `HtmlDocumentRenderer` and `HtmlRenderingOptions`
  without HTTP framework types. Evaluation remains synchronous. Ktor integration
  is supplied separately by `spa-routing-ktor`. See [the runtime guide](docs/runtime.md).

- **The remaining API types and helpers drop the `Spa` prefix.** Update imports
  and usages, then regenerate routes and recompile consumers. Apply the runtime
  package migration above as well; no aliases for the old names are provided.

  | Previous name | New name |
  | --- | --- |
  | `SpaRouteParameter` | `RouteParameter` |
  | `SpaRouteResponseEvaluator` | `RouteAccessEvaluator` |
  | `SpaRouteHttpResponse` | `RouteHttpResponse` |
  | `SpaRouteResponseRequest` | `RouteRequest` |
  | `SpaRouteResponseService` | `RouteResponseService` |
  | `SpaRouteRequestFactory` | `RouteRequestFactory` |
  | `DefaultSpaRouteRequestFactory` | `DefaultRouteRequestFactory` |
  | `SpaHtmlRenderer` | `HtmlRenderer` |
  | `DefaultSpaHtmlRenderer` | `DefaultHtmlRenderer` |
  | `SpaRouterFunctionFactory` | `SpringRouterFunctionFactory` |
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

  These types live in `com.sparouting.contract`. Regenerate server routes with
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
  `SinglePageApplicationDefinition` exposes
  `getFullPathPattern(route)`, taking a `RouteDefinition` and returning its
  prefixed path pattern with parameter placeholders intact. The string overload
  was removed. Parameter substitution is handled separately by `resolvePath`.

  Migration: replace `getFullUrl(route)` or `getFullUrl(route.path)` with
  `getFullPathPattern(route)`, and `getFullUrl(route.resolvePath(values))` with
  `route.resolvePath(getFullPathPattern(route), values)`. Runtime configs now use
  manifests instead, so replace their former URL helpers with `RouteManifest.path`
  or `RouteManifest.resolvePath(values)`. Query-string handling is unchanged.

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

- `spa-routing-ktor` provides `com.sparouting.ktor.singlePageApplicationRoutes`
  for reusable Ktor integration. Register application configs containing their
  HTML renderers to serve generated page paths and
  one shared decision endpoint. Its `routeDecisionPath` argument defaults to
  `/__spa/route-decision`; clients must use the same configured URL.
  The adapter converts real headers and
  decoded path/query values, returns statuses or redirects for pages, and returns
  navigation results as HTTP `200` JSON with `Cache-Control: no-store`.
  Applications choose their engine, configure JSON content negotiation, and
  serve their own assets. The Ktor blog now consumes this library module.

- A Ktor blog example reuses the Spring example's service logic, validation,
  access handlers, generated Kotlin config, and React frontend. Shared code lives
  in `examples:blog` and assets in `examples:frontend`; the servers implement
  their own HTTP endpoints and wiring. Run `:examples:ktor:run` on port 8082 or
  `:examples:spring:run` on port 8080. Both remain unpublished example projects.

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
