# Framework-neutral runtime

`spa-routing-runtime` contains server behavior shared by framework adapters. It
depends on `spa-routing-core` and has no Spring, Servlet, or Ktor dependency.
Its public types live under `com.sparouting.runtime`.

| Module | Responsibility |
| --- | --- |
| `spa-routing-core` | Authoring definitions, route and application contracts, request models, access handler contracts, and generators |
| `spa-routing-runtime` | Route registry, handler registration validation, application and route access checks, redirect resolution, and HTML document generation |
| `spa-routing-spring-boot-autoconfigure` | Bean discovery, properties, MVC route registration, request conversion, HTTP/JSON responses, and Spring rendering hooks |
| `spa-routing-spring-boot-starter` | Dependencies for Spring Boot applications |

Spring is currently the only supplied adapter. A future Ktor adapter can call
the same runtime service. Evaluation is synchronous; suspendable access
handlers are outside this extraction.

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

Each config owns a required application handler and route-handler collection.
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
val config = BlogApplicationConfig(
  applicationAccessHandler = CheckBlogAccess(),
  routeAccessHandlers = BlogRouteAccessHandlers(
    post = checkPostAccess,
    editPost = checkEditPostAccess
  )
)
```

This wiring is plain Kotlin and works with manual construction or any DI container.

An adapter or dependency injection container assembles the runtime once:

```kotlin
import com.sparouting.runtime.access.RouteAccessEvaluator
import com.sparouting.contract.SinglePageApplicationConfig
import com.sparouting.runtime.config.SinglePageApplicationRouteRegistry
import com.sparouting.runtime.response.RouteResponseService

fun createRouteService(
  configs: List<SinglePageApplicationConfig>
): RouteResponseService {
  val routes = SinglePageApplicationRouteRegistry(configs)
  return RouteResponseService(
    routeRegistry = routes,
    accessEvaluator = RouteAccessEvaluator(routes),
    invalidPathParameterStatus = 400,
    invalidQueryStringStatus = 400
  )
}
```

`SinglePageApplicationRouteRegistry` validates registrations during construction.
It reads handlers from each config instead of discovering them globally.
Generated constructors enforce application-handler and route-handler types.
For custom config or collection implementations, runtime validation still rejects
missing, duplicate, unknown-route, unflagged-route, and wrong-application route
handlers. Spring discovers config beans and supplies those to the registry.

Each `SinglePageApplicationRouteRegistration` contains the config, route manifest,
required `applicationAccessHandler`, and optional `routeAccessHandler`.
The evaluator's `evaluate(request)` method retrieves this registration in one
lookup. It owns no separate handler map and invokes the application handler first.
Only `AccessDecision.Allow` proceeds to the matching `RouteAccessHandler`.
If there is no route handler, the application's allowance is sufficient.
Either handler can return `AccessDecision.Redirect` with an unresolved typed
target. The evaluator returns that decision; `RouteResponseService` resolves
redirects and constructs response data.

Application handlers receive the framework-neutral `RouteRequest`. Typed route
handlers receive their generated request models. Both return core's
`AccessDecision`; neither has a skip result or an ordered rule chain.

Direct evaluator calls require a registered route and validated parameters.
An unknown application or route throws `IllegalArgumentException`; adapters
should use `RouteResponseService`, which returns `404` before access evaluation.

## Adapt requests and responses

Use `RouteResponseService.evaluate(RouteRequest)` for both page requests and
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

Both look up the route, validate path and declared query values, then run the
application and route access checks in order. Unknown routes return `404`.
Invalid input stops before either handler. An application redirect stops the
route check. Typed redirects are validated and resolved by the response
service for both entry points.

The result is `RouteHttpResponse(statusCode, location)`. It contains no native
framework response or serialization annotations. The adapter chooses how to
write it: Spring renders HTML for an allowed page request, sends a status or
redirect for other page outcomes, and returns HTTP `200` with the result as JSON
and `Cache-Control: no-store` for a navigation check.

An adapter also owns URL matching, decoding, request authentication context,
and HTTP serialization. Application access handlers that use a framework's security
context will need an equivalent implementation when moving frameworks.

## Render the default HTML document

`HtmlDocumentRenderer` returns an HTML string with escaped application metadata
and asset URLs. Adapters set the response status and content type:

```kotlin
import com.sparouting.runtime.rendering.HtmlDocumentRenderer
import com.sparouting.runtime.rendering.HtmlRenderingOptions

val html = HtmlDocumentRenderer(
  HtmlRenderingOptions(
    bundleBasePath = "/bundles",
    includeRouteStylesheet = true,
    globalStylesheet = "/bundles/stylex.css"
  )
).render(config)
```

Read application metadata directly from `config.id`, `.name`, `.bundleName`, and
`.routes`. Each route is a core `RouteManifest` value.
Spring applications with a per-application `ServerResponse` override use
`SpringSinglePageApplicationConfig`; global HTTP rendering remains a Spring
`HtmlRenderer` bean. See the [Spring guide](spring-boot-client-apps.md#render-html).
