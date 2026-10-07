# Framework-neutral runtime

`spa-routing-runtime` contains server behavior shared by framework adapters. It
depends on `spa-routing-core` and has no Spring, Servlet, or Ktor dependency.
Its public types live under `com.sparouting.runtime`.

| Module | Responsibility |
| --- | --- |
| `spa-routing-core` | Authoring definitions, route and application contracts, request models, access handler contracts, and generators |
| `spa-routing-runtime` | Route registry, handler registration validation, application and route access checks, destination resolution, HTTP metadata conversion, and HTML document generation |
| `spa-routing-ktor` | Ktor route registration, request conversion, and HTTP/JSON responses |
| `spa-routing-spring-boot-autoconfigure` | Bean discovery, properties, MVC route registration, request conversion, HTTP/JSON responses, and HTML delivery |
| `spa-routing-spring-boot-starter` | Dependencies for Spring Boot applications |

Spring and Ktor adapters call the same runtime evaluator. See the
[Ktor setup](ktor.md) and [blog examples](../examples/README.md), which share
their blog service, access handlers, generated config, and frontend.
Evaluation is synchronous; suspendable access handlers are not supported.

## Wire the runtime

`generateServerRoutes` emits an application-specific config and typed handler
contracts. For the blog these are `BlogApplicationConfig`, `BlogApplicationAccessHandler`,
and `BlogRouteAccessHandlers`, alongside the existing route objects and typed
route handlers. Generated server sources depend only on `spa-routing-core`.
The shared config, handler, and request contracts live in `com.sparouting.contract`;
the runtime consumes those contracts to register and evaluate routes. Neither
module depends on an HTTP framework.

`SinglePageApplicationConfig` is the shared configuration interface in core. Generated configs
provide final `id`, `name`, `bundleName`, and `routes` properties, with full route
paths, parameter metadata, and handler requirements. Authoring definitions and
build-only fields such as `appRootPath` stay on the generator classpath.

Each config owns a required application handler, route-handler collection, and HTML renderer.
`BlogApplicationAccessHandler` extends `ApplicationAccessHandler<BlogApplicationConfig>`;
`BlogRouteAccessHandlers` implements `RouteAccessHandlers<BlogApplicationConfig>` and requires
one constructor argument of the generated handler type for every gated route.
Adding a gated route adds a required argument, so incomplete wiring fails to
compile. Applications without gated routes get a zero-argument collection.
The generic config parameter associates handlers with their application; handlers
do not receive a config instance and there is no circular injection.

Public applications explicitly allow access:

```kotlin
import com.sparouting.examples.generated.routes.BlogApplicationAccessHandler
import com.sparouting.contract.AccessDecision
import com.sparouting.contract.RouteRequest

class CheckBlogAccess : BlogApplicationAccessHandler() {
  override fun evaluate(request: RouteRequest): AccessDecision = AccessDecision.Allowed
}
```

Construct the generated config directly; no subclass is required:

```kotlin
import com.sparouting.runtime.rendering.HtmlDocumentRenderer

val config = BlogApplicationConfig(
  applicationAccessHandler = CheckBlogAccess(),
  routeAccessHandlers = BlogRouteAccessHandlers(
    post = checkPostAccess,
    editPost = checkEditPostAccess
  ),
  htmlRenderer = HtmlDocumentRenderer()
)
```

This wiring is plain Kotlin and works with manual construction or any DI container.

Spring and Ktor construct the evaluator internally from the supplied configs.
When writing another adapter, construct one evaluator for all applications and
reuse it for page requests and navigation checks:

```kotlin
import com.sparouting.runtime.evaluation.RouteRequestEvaluator
import com.sparouting.runtime.evaluation.DefaultRouteFailureHandler

val failureHandler = DefaultRouteFailureHandler(
  unknownRouteDestination = BlogRoutes.NotFound(),
  invalidRequestDestination = BlogRoutes.Error()
)
val evaluator = RouteRequestEvaluator(configs = listOf(config), failureHandler = failureHandler)
```

`RouteRequestEvaluator` builds and validates its registrations during construction.
It reads handlers from each config instead of discovering them globally.
Generated constructors enforce application-handler and route-handler types.
For custom config or collection implementations, runtime validation still rejects
missing, duplicate, unknown-route, unflagged-route, and wrong-application route
handlers. The registry is an internal implementation detail.
Customize access through the handlers supplied in each application config.

Application handlers receive the framework-neutral `RouteRequest`. Typed route
handlers receive their generated request models. Both return core's `AccessDecision`:
`Allowed` permits navigation, while `Denied(reason, destination)` supplies a
`DenialReason(code, message)` and typed `RouteTarget`. Applications define the reason
codes and user-facing messages. Neither handler has a skip result or an ordered rule chain.

## Adapt requests and responses

Use `RouteRequestEvaluator.evaluate(RouteRequest)` for both page requests and
client navigation checks. Adapters supply application and route IDs, decoded
`pathParameters` and `queryString` values, and headers from the actual incoming
request. Both entry points use the same request type and validation/access pipeline.

`RouteRequest` contains no HTTP method or raw request path. Typed route handlers
receive `RouteAccessContext` with method `GET` and the destination path resolved
from the route metadata. This gives handlers the same target context for page loads
and navigation checks, alongside the caller's headers and query values.

Route paths in the config already include the application prefix. Adapters
register `route.path` directly; `route.resolvePath(parameters)` substitutes path
values for navigation and redirects.

The evaluator looks up the route once, validates path and declared query values,
then runs the application and route access checks in order. Invalid input stops
before either handler. An application denial stops the route check. Typed
denial destinations are validated and resolved to URLs by the evaluator, preserving
the supplied reason.

Evaluation returns `com.sparouting.runtime.evaluation.RouteResult`, with a `type`
discriminator. Every failure carries a required `destination` URL and `reason`:

| Result | JSON `type` | Default page response |
| --- | --- | --- |
| `Allowed` | `allowed` | `200` with rendered HTML |
| `Denied(reason, destination)` | `denied` | `302` to the destination |
| `UnknownRoute(reason, destination)` | `unknown_route` | `302` to the destination |
| `InvalidRequest(reason, destination)` | `invalid_request` | `302` to the destination |

`UnknownRoute` means the requested application or route ID is not registered.
`InvalidRequest` means path or declared query parameters violate the route's contract.
Resource existence remains an application concern.

Navigation endpoints serialize this result directly inside HTTP `200`, with
`Cache-Control: no-store`. Allowed responses contain only `{"type":"allowed"}`.
Every failure includes a reason and destination, with no `statusCode` or `location`:

```json
{
  "type": "unknown_route",
  "destination": "/not-found",
  "reason": {"code": "unknown_route", "message": "That page does not exist."}
}
```

Clients continue for `allowed` and navigate to `destination` otherwise. The failure
type and reason remain available for application-specific messaging. A page redirect
carries the destination in its `Location` header; it does not transport the reason.

## Configure recovery destinations

Supply a framework-neutral `RouteFailureHandler` for the whole registration:
a Spring bean or Ktor's required `failureHandler` argument. It also handles unknown
application IDs, so it is independent of any one application config.

`DefaultRouteFailureHandler(unknownRouteDestination, invalidRequestDestination)`
accepts typed `RouteTarget` values and provides standard reason codes/messages.
Implement `unknownRoute(request)` and `invalidRequest(request)` to customize recovery
using the original IDs, path/query values, and actual headers. Both methods return
`AccessDecision.Denied`; the evaluator preserves the original failure type and resolves
the supplied target using the same validation and encoding as access-handler denials.
Recovery routes must be registered, valid targets that the caller can reach.

Both page requests and navigation checks run this handler before HTTP conversion.
Native framework URL misses still follow the framework's routing behavior; the
handler applies to requests evaluated by this library.

## Customize HTTP conversion

`RouteHttpResponseConverter.convert(request, result)` receives the resolved outcome
for page requests only. Its default maps `Allowed` to `200` and every failure to
`302` with the supplied destination. Replace it with a Spring bean or Ktor's
`responseConverter` argument to customize page HTTP metadata.

The converter returns `RouteHttpResponse(statusCode, location)`. It does not affect
navigation JSON. Put access checks and recovery decisions in the shared handlers;
use the converter for HTTP-specific presentation. The former `invalidRequestStatus`
argument and Spring property are removed.

HTML renders only for `Allowed` converted to `200` without a location. Mapping a
failure to `200` never renders it, and mapping an allowed request to an error or
redirect skips rendering. Rendering remains configured through `htmlRenderer`.

## Configure HTML rendering

`com.sparouting.contract.HtmlRenderer` is a core interface with
`render(application: SinglePageApplicationConfig): String`. Supply it through the
generated config's required `htmlRenderer` constructor argument. Both adapters
use that application's renderer only for allowed page loads; navigation checks,
denials, and validation errors do not render HTML.

The runtime's `HtmlDocumentRenderer` implements this interface and escapes
application metadata and asset URLs. Configure its asset options at construction:

```kotlin
import com.sparouting.runtime.rendering.HtmlDocumentRenderer

val renderer = HtmlDocumentRenderer(
  bundleBasePath = "/bundles",
  includeRouteStylesheet = true,
  globalStylesheet = "/bundles/stylex.css"
)
```

Read application metadata directly from `config.id`, `.name`, `.bundleName`, and
`.routes`. Each route is a core `RouteManifest` value.
Pass the same renderer to multiple configs to share rendering, or a different
implementation for each application. Renderers return HTML strings; adapters set
HTTP status and content type. Generated code depends only on the core interface.
