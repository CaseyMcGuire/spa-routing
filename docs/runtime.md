# Framework-neutral runtime

`spa-routing-runtime` contains server behavior shared by framework adapters. It
depends on `spa-routing-core` and has no Spring, Servlet, or Ktor dependency.
Its public types live under `com.sparouting.runtime`.

| Module | Responsibility |
| --- | --- |
| `spa-routing-core` | Authoring definitions, route and application contracts, request models, access handler contracts, and generators |
| `spa-routing-runtime` | Route registry, handler registration validation, application and route access checks, redirect resolution, and HTML document generation |
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
  override fun evaluate(request: RouteRequest): AccessDecision = AccessDecision.Allow
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

val evaluator = RouteRequestEvaluator(configs = listOf(config))
```

`RouteRequestEvaluator` builds and validates its registrations during construction.
It reads handlers from each config instead of discovering them globally.
Generated constructors enforce application-handler and route-handler types.
For custom config or collection implementations, runtime validation still rejects
missing, duplicate, unknown-route, unflagged-route, and wrong-application route
handlers. The registry is an internal implementation detail.
Customize access through the handlers supplied in each application config.

Application handlers receive the framework-neutral `RouteRequest`. Typed route
handlers receive their generated request models. Both return core's
`AccessDecision`; neither has a skip result or an ordered rule chain.

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
before either handler. An application redirect stops the route check. Typed
redirects are validated and resolved to URLs by the evaluator.

Evaluation returns `com.sparouting.runtime.evaluation.RouteResult`, with no HTTP
status codes. Each adapter maps it explicitly:

| Result | Page response |
| --- | --- |
| `Allowed` | `200` with the config's rendered HTML |
| `Redirect(url)` | `302` with a `Location` header |
| `NotFound` | `404` |
| `InvalidPathParameters` | Configured path-validation status, default `400` |
| `InvalidQueryString` | Configured query-validation status, default `400` |

Validation status settings belong to the adapters. Only `Allowed` renders HTML,
even if a validation status is configured as `200`.

For navigation checks, the adapter maps the result to the shared JSON payload
`RouteDecisionResponse(statusCode, location)` in `com.sparouting.runtime.response`.
The endpoint itself returns HTTP `200` and `Cache-Control: no-store`. The payload
describes the target page's status and redirect; it is not the evaluator's return type.

An adapter also owns URL matching, decoding, request authentication context,
and HTTP serialization. Application access handlers that use a framework's security
context will need an equivalent implementation when moving frameworks.

## Configure HTML rendering

`com.sparouting.contract.HtmlRenderer` is a core interface with
`render(application: SinglePageApplicationConfig): String`. Supply it through the
generated config's required `htmlRenderer` constructor argument. Both adapters
use that application's renderer only for allowed page loads; navigation checks,
redirects, and validation errors do not render HTML.

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
