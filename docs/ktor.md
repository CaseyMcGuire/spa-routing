# Ktor integration

`spa-routing-ktor` adapts the shared runtime to Ktor. It registers generated page
paths and a single navigation decision endpoint for all supplied applications.
The module depends on `spa-routing-runtime` and Ktor server core. Applications
choose the server engine, JSON converter, and asset serving.

## Dependencies

This guide targets `0.5.0`; see the [installation instructions](../README.md#install).

The adapter targets Ktor 3.6.0 and JDK 21. For example, using Netty and Jackson:

```kotlin
dependencies {
  implementation("io.github.caseymcguire:spa-routing-ktor:0.5.0")
  implementation(platform("io.ktor:ktor-bom:3.6.0"))
  implementation("io.ktor:ktor-server-netty")
  implementation("io.ktor:ktor-server-content-negotiation")
  implementation("io.ktor:ktor-serialization-jackson")
}
```

The repository's [Ktor blog example](../examples/README.md) uses the local
`:spa-routing-ktor` project dependency.

## Register applications

Construct your generated configs with their access handlers and HTML renderer,
as described in the [runtime guide](runtime.md), then pass the configs to the adapter:

```kotlin
import com.sparouting.contract.SinglePageApplicationConfig
import com.sparouting.ktor.singlePageApplicationRoutes
import com.sparouting.runtime.evaluation.RouteFailureHandler
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing

fun Application.registerApplications(configs: List<SinglePageApplicationConfig>, failureHandler: RouteFailureHandler) {
  install(ContentNegotiation) {
    jackson()
  }
  routing {
    singlePageApplicationRoutes(configs = configs, failureHandler = failureHandler)
    staticResources("/bundles", "static/bundles")
  }
}
```

Call `singlePageApplicationRoutes` once at the routing root. It constructs one
shared evaluator for all supplied configs. Route metadata already contains full paths,
including application prefixes. The extension passes these patterns to Ktor's
`get` function and reads decoded values from `call.pathParameters`. Keep patterns
compatible with Ktor's path syntax; mounting the extension beneath another path
would change URLs without updating generated client URLs or denial destinations.

The evaluator validates access-handler registration at startup and handles request
validation, application access, route access, and destination resolution. It returns
a `RouteResult`. The required `RouteFailureHandler` supplies recovery destinations for
unknown routes and invalid requests, shared by pages and client navigation. See
[recovery configuration](runtime.md#configure-recovery-destinations). Handlers remain synchronous.

Install content negotiation with a converter capable of serializing the plain
Kotlin `RouteResult` variants, such as Jackson. The adapter does not install
a converter or add serialization annotations to core/runtime models.

## Customize HTTP responses

Pass a framework-neutral `RouteHttpResponseConverter` to replace the default
status/location mapping for page responses:

```kotlin
singlePageApplicationRoutes(
  configs = configs,
  failureHandler = failureHandler,
  responseConverter = myResponseConverter
)
```

See [custom HTTP conversion](runtime.md#customize-http-conversion). Navigation JSON
serializes the semantic result directly and does not invoke this converter.

## Configure the decision endpoint

The shared navigation endpoint defaults to `/__spa/route-decision`. Set
`routeDecisionPath` when registering the routes to use another URL:

```kotlin
singlePageApplicationRoutes(
  configs = configs,
  failureHandler = failureHandler,
  routeDecisionPath = "/internal/navigation"
)
```

Configure the client authorization middleware to call the same URL. The chosen
path applies to every supplied application and replaces the default endpoint.
Page route paths are unaffected.

## Page and navigation responses

With the default converter, an allowed request receives HTML from the matching
config's `htmlRenderer`. Every failure produces an HTTP `302` with its configured
destination in a `Location` header.
Unregistered URLs retain Ktor's normal routing behavior.

Rendering requires an `Allowed` result converted to `200` without a location.
Custom redirects/errors skip HTML; invalid or denied requests never render it.

Client navigation uses `GET` at the configured decision path, defaulting to
`/__spa/route-decision`, with the same wire format as Spring:

```text
applicationId=blog
routeId=Post
parameters.postId=1
queryString.tab=details
```

The endpoint returns HTTP `200` with `Cache-Control: no-store`. Its JSON body
describes the target page outcome:

```json
{
  "type": "denied",
  "destination": "/not-found"
}
```

Page loads and navigation decisions both pass actual request headers to
`RouteRequestEvaluator.evaluate`. Repeated query values retain their cardinality,
and path values are kept separate from query values. Navigation results share
the same validation and access checks as page requests.

The adapter does not register application REST APIs or static files. Register
those alongside it, as shown by `blogApiRoutes` and `staticResources` in the
example. Each application config owns its renderer and any renderer options.
