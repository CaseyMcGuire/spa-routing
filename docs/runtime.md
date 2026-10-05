# Framework-neutral runtime

`spa-routing-runtime` contains server behavior shared by framework adapters. It
depends on `spa-routing-core` and has no Spring, Servlet, or Ktor dependency.
Its public types live under `io.github.caseymcguire.sparouting.runtime`.

| Module | Responsibility |
| --- | --- |
| `spa-routing-core` | Route definitions, generated route contracts, access handler contracts, and generators |
| `spa-routing-runtime` | Application configuration, route registry, handler registration validation, application and route access checks, redirect resolution, and HTML document generation |
| `spa-routing-spring-boot-autoconfigure` | Bean discovery, properties, MVC route registration, request conversion, HTTP/JSON responses, and Spring rendering hooks |
| `spa-routing-spring-boot-starter` | Dependencies for Spring Boot applications |

Spring is currently the only supplied adapter. A future Ktor adapter can call
the same runtime service. Evaluation is synchronous; suspendable access
handlers are outside this extraction.

## Wire the runtime

Keep the same application definition and generated access handlers across
frameworks. Supply an application access handler explicitly. Public applications return
`AccessDecision.Allow`:

```kotlin
import com.sparouting.contract.AccessDecision
import io.github.caseymcguire.sparouting.runtime.access.ApplicationAccessHandler
import io.github.caseymcguire.sparouting.runtime.config.SinglePageApplicationConfig
import io.github.caseymcguire.sparouting.runtime.request.RouteRequest

class CheckBlogAccess : ApplicationAccessHandler(BlogApplication) {
  override fun evaluate(request: RouteRequest): AccessDecision = AccessDecision.Allow
}

val config = object : SinglePageApplicationConfig {
  override val application = BlogApplication
}
```

An adapter or dependency injection container assembles the runtime once:

```kotlin
import com.sparouting.contract.RouteAccessHandler
import io.github.caseymcguire.sparouting.runtime.access.ApplicationAccessHandler
import io.github.caseymcguire.sparouting.runtime.access.RouteAccessEvaluator
import io.github.caseymcguire.sparouting.runtime.config.SinglePageApplicationConfig
import io.github.caseymcguire.sparouting.runtime.config.SinglePageApplicationRouteRegistry
import io.github.caseymcguire.sparouting.runtime.response.RouteResponseService

fun createRouteService(
  configs: List<SinglePageApplicationConfig>,
  applicationHandlers: List<ApplicationAccessHandler>,
  routeHandlers: List<RouteAccessHandler<*>>
): RouteResponseService {
  val routes = SinglePageApplicationRouteRegistry(configs, applicationHandlers, routeHandlers)
  return RouteResponseService(
    routeRegistry = routes,
    accessEvaluator = RouteAccessEvaluator(routes),
    invalidPathParameterStatus = 400,
    invalidQueryStringStatus = 400
  )
}
```

`SinglePageApplicationRouteRegistry` validates registrations during construction.
It requires exactly one application handler per configured application, even
for applications with no routes, and rejects handlers for unknown applications.
It also rejects missing, duplicate, unknown-route, and unflagged-route handlers.
Spring collects both handler types and performs this wiring through its starter.

Each `SinglePageApplicationRouteRegistration` contains the config, route definition,
required `applicationAccessHandler`, and optional `routeAccessHandler`.
The evaluator's `evaluate(request)` method retrieves this registration in one
lookup. It owns no separate handler map and invokes the application handler first.
Only `AccessDecision.Allow` proceeds to the matching `RouteAccessHandler`.
If there is no route handler, the application's allowance is sufficient.
Either handler can return `AccessDecision.Redirect` with an unresolved typed
target. The evaluator returns that decision; `RouteResponseService` resolves
redirects and constructs response data.

`ApplicationAccessHandler` is an abstract class whose constructor takes the
application definition it protects. Register implementations through your DI
container, separately from application configs. Application handlers receive
the framework-neutral `RouteRequest`. Typed route handlers continue receiving
their generated request models. Both return core's
`AccessDecision`; neither has a skip result or an ordered rule chain.

Direct evaluator calls require a registered route and validated parameters.
An unknown application or route throws `IllegalArgumentException`; adapters
should use `RouteResponseService`, which returns `404` before access evaluation.

## Adapt requests and responses

Use `RouteResponseService` as the entry point for both kinds of request:

- A page request calls `evaluate(RouteRequest)`. The adapter supplies application
  and route IDs, actual method and path, decoded path/query values, and headers.
- A client navigation check calls `evaluate(RouteResponseRequest)`. The runtime
  resolves the target route and builds its GET request. Pass headers from the
  actual client request.

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
import io.github.caseymcguire.sparouting.runtime.rendering.HtmlDocumentRenderer
import io.github.caseymcguire.sparouting.runtime.rendering.HtmlRenderingOptions

val html = HtmlDocumentRenderer(
  HtmlRenderingOptions(
    bundleBasePath = "/bundles",
    includeRouteStylesheet = true,
    globalStylesheet = "/bundles/stylex.css"
  )
).render(config)
```

`SinglePageApplicationConfig` contains the application definition; access handlers are registered separately.
Spring applications with a per-application `ServerResponse` override use
`SpringSinglePageApplicationConfig`; global HTTP rendering remains a Spring
`HtmlRenderer` bean. See the [Spring guide](spring-boot-client-apps.md#render-html).
