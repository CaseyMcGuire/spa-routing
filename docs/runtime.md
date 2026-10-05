# Framework-neutral runtime

`spa-routing-runtime` contains server behavior shared by framework adapters. It
depends on `spa-routing-core` and has no Spring, Servlet, or Ktor dependency.
Its public types live under `io.github.caseymcguire.sparouting.runtime`.

| Module | Responsibility |
| --- | --- |
| `spa-routing-core` | Route definitions, generated route contracts, access handler contracts, and generators |
| `spa-routing-runtime` | Application configuration, route registry, handler registration validation, application rules, access evaluation, redirect resolution, and HTML document generation |
| `spa-routing-spring-boot-autoconfigure` | Bean discovery, properties, MVC route registration, request conversion, HTTP/JSON responses, and Spring rendering hooks |
| `spa-routing-spring-boot-starter` | Dependencies for Spring Boot applications |

Spring is currently the only supplied adapter. A future Ktor adapter can call
the same runtime service. Evaluation is synchronous; suspendable rules and
handlers are outside this extraction.

## Wire the runtime

Keep the same application definition and generated access handlers across
frameworks. Configure an application gate explicitly, including `AllowAll()`
for public applications:

```kotlin
import io.github.caseymcguire.sparouting.runtime.config.SinglePageApplicationConfig
import io.github.caseymcguire.sparouting.runtime.rules.builtin.AllowAll

val config = object : SinglePageApplicationConfig {
  override val application = BlogApplication
  override val rules = listOf(AllowAll())
}
```

An adapter or dependency injection container assembles the runtime once:

```kotlin
import com.sparouting.contract.RouteAccessHandler
import io.github.caseymcguire.sparouting.runtime.access.RouteAccessEvaluator
import io.github.caseymcguire.sparouting.runtime.config.SinglePageApplicationConfig
import io.github.caseymcguire.sparouting.runtime.config.SinglePageApplicationRouteRegistry
import io.github.caseymcguire.sparouting.runtime.response.RouteResponseService
import io.github.caseymcguire.sparouting.runtime.rules.RouteRuleActionResolver

fun createRouteService(
  configs: List<SinglePageApplicationConfig>,
  handlers: List<RouteAccessHandler<*>>
): RouteResponseService {
  val routes = SinglePageApplicationRouteRegistry(configs)
  return RouteResponseService(
    routeRegistry = routes,
    accessEvaluator = RouteAccessEvaluator(routes, handlers),
    actionResolver = RouteRuleActionResolver(configs),
    invalidPathParameterStatus = 400,
    invalidQueryStringStatus = 400
  )
}
```

`RouteAccessEvaluator` validates registrations during construction, even when
the handler list is empty. It rejects missing, duplicate, and stale handlers
for routes with `generateAccessHandler`. Spring performs this wiring
automatically through its starter.

The evaluator runs application rules and the matching handler, returning
`RouteRuleResult.Allow` or `RouteRuleResult.Deny`. Redirects remain unresolved
actions at this point. `RouteResponseService` owns request validation and
response conversion, using `RouteRuleActionResolver` to resolve those actions.
The evaluator does not construct HTTP responses. If a custom evaluator returns
`Skip`, the service denies access with `404`.

## Adapt requests and responses

Use `RouteResponseService` as the entry point for both kinds of request:

- A page request calls `evaluate(RouteRequest)`. The adapter supplies application
  and route IDs, actual method and path, decoded path/query values, and headers.
- A client navigation check calls `evaluate(RouteResponseRequest)`. The runtime
  resolves the target route and builds its GET request. Pass headers from the
  actual client request.

Both look up the route, validate path and declared query values, evaluate the
application gate, then invoke the route's handler. Unknown routes return `404`.
Invalid input stops before rules and handlers. A missing application-level
allow decision returns `404`; a route without a handler is allowed after the
application gate passes. Typed redirects use the same resolver in both paths.

The result is `RouteHttpResponse(statusCode, location)`. It contains no native
framework response or serialization annotations. The adapter chooses how to
write it: Spring renders HTML for an allowed page request, sends a status or
redirect for other page outcomes, and returns HTTP `200` with the result as JSON
and `Cache-Control: no-store` for a navigation check.

An adapter also owns URL matching, decoding, request authentication context,
and HTTP serialization. Application rules that use a framework's security
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

`SinglePageApplicationConfig` contains definition and rule configuration only.
Spring applications with a per-application `ServerResponse` override use
`SpringSinglePageApplicationConfig`; global HTTP rendering remains a Spring
`HtmlRenderer` bean. See the [Spring guide](spring-boot-client-apps.md#render-html).
