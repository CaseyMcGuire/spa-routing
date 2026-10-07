# Spring Boot Client Apps

This guide is for Spring Boot applications that want to serve SPA routes using
`spa-routing`.

Use authored `SinglePageApplicationDefinition` objects to generate application
configs and typed routes, then let the Spring starter handle:

- registering MVC `GET` routes for each SPA route
- validating path parameters
- evaluating application and route access handlers
- resolving typed denial destinations
- rendering a default SPA HTML page
- exposing a route decision endpoint for client-side navigation checks

Your application still owns:

- route definitions
- `SinglePageApplicationConfig` beans
- app-specific access handlers
- custom HTML rendering, if the default page is not enough
- any custom GraphQL or REST route decision endpoint, if you do not want the built-in endpoint

## Add Dependencies

This guide targets unreleased `0.5.0-SNAPSHOT`; see the [local installation instructions](../README.md#install).

Add the Spring Boot starter to the Spring application that will serve the SPA
routes:

```kotlin
dependencies {
  implementation("io.github.caseymcguire:spa-routing-spring-boot-starter:0.5.0-SNAPSHOT")
}
```

Keep `SinglePageApplicationDefinition` objects in `:spa-route-definitions`
and configure that project as the generation input below. It belongs on the
generator classpath, not the server's implementation/runtime classpath. The
starter supplies the contracts needed to compile generated configs and routes.

The starter targets Spring Boot 4.x and brings in `spring-boot-starter-web`.
Use it from Spring Boot 4 applications.

Prefer the starter for normal applications. If you depend directly on
`spa-routing-spring-boot-autoconfigure`, your app still needs Spring Boot and
Spring MVC on its classpath because the adapter exposes Spring MVC types such
as `ServerResponse`. Shared runtime types come from the transitive
`spa-routing-runtime` dependency and have no Spring dependency. See the
[framework-neutral runtime guide](runtime.md) for the adapter boundary.

## Public Packages

Client apps usually import from these packages:

Packages below share the prefix `com.sparouting`:

- `contract`: application configuration, route metadata, access and rendering contracts, and framework-neutral request models
- `runtime.config`: configuration validation
- `runtime.evaluation`: shared request evaluator and semantic route results
- `runtime.response`: HTTP response converter, default mapping, and response metadata
- `runtime.rendering`: HTML document builder and asset options
- `spring.request`: Spring request factory
- `spring.response`: Spring response conversion
- `spring.web`: Spring MVC router factories
- `spring.autoconfigure`: Spring Boot properties and auto-configuration

The shared contracts live in `com.sparouting.contract` in `spa-routing-core`.
Generated server configs and handlers need only core to compile; the starter
adds the runtime that registers routes and evaluates access.
Apply the Gradle plugin to generate configs, route builders, and access handlers
(or depend on a module containing that output):

```kotlin
plugins {
  id("io.github.caseymcguire.spa-routing") version "0.5.0-SNAPSHOT"
}

spaRouting {
  routeDefinitions {
    projectPath = ":spa-route-definitions"
  }

  serverRoutes {
    packageName = "com.example.generated.spa.routes"
    sourceRoot = "build/generated/source/spaRoutes/main"
  }

  clientRoutes {
    outputDirectory = "src/main/web-frontend/__generated__/routes"
  }

  bundleEntries {
    outputFile = "SinglePageApplicationBundles.ts"
  }
}
```

The `generateBundleEntries` task writes a bundler-neutral, default-exported map
of bundle name to app root path. webpack consumes it as `entry` directly, while
a Vite config feeds it to `build.rollupOptions.input` and pins output names to
what the default HTML renderer expects:

```ts
import { resolve } from "node:path"
import bundles from "./SinglePageApplicationBundles"

export default defineConfig({
  build: {
    rollupOptions: {
      input: Object.fromEntries(
        Object.entries(bundles).map(([name, path]) => [name, resolve(__dirname, path)])
      ),
      output: {
        entryFileNames: "[name].bundle.js",
        assetFileNames: "[name][extname]",
      },
    },
  },
})
```

Note that Rollup does not resolve a directory to its `index` file the way
webpack does, so with Vite each application's `appRootPath` must point at the
entry file itself (e.g. `src/main/web-frontend/apps/account/index.tsx`), or
your Vite config must append the entry filename when building the input map.

## Define Config Beans

For each SPA that should be served, expose its generated application config as
a bean. `generateServerRoutes` emits a concrete `AccountApplicationConfig`,
`AccountApplicationAccessHandler`, and `AccountRouteAccessHandlers` alongside
`AccountRoutes`. Instantiate the config directly with its handlers and renderer:

```kotlin
package com.example.web

import com.example.generated.spa.routes.AccountApplicationConfig
import com.example.generated.spa.routes.AccountRouteAccessHandlers
import com.example.generated.spa.routes.AccountRoutes
import com.sparouting.runtime.evaluation.DefaultRouteFailureHandler
import com.sparouting.runtime.evaluation.RouteFailureHandler
import com.sparouting.runtime.rendering.HtmlDocumentRenderer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class RoutesConfiguration {
  @Bean
  fun routeFailureHandler(): RouteFailureHandler = DefaultRouteFailureHandler(
    unknownRouteDestination = AccountRoutes.NotFound(),
    invalidRequestDestination = AccountRoutes.Error()
  )

  @Bean
  fun accountConfig(applicationAccessHandler: CheckAccountAccess): AccountApplicationConfig =
    AccountApplicationConfig(
      applicationAccessHandler = applicationAccessHandler,
      routeAccessHandlers = AccountRouteAccessHandlers(),
      htmlRenderer = HtmlDocumentRenderer()
    )
}
```

Declare `NotFound` and `Error` recovery routes in the application definition.
This example has no gated routes. When a route declares `generateAccessHandler = true`,
its typed handler becomes a required constructor argument in `AccountRouteAccessHandlers`.
Supply the implementations through constructor injection as shown below for the blog.
Generated code has no Spring annotations and also works with other DI containers.

The config implements `SinglePageApplicationConfig`. Read metadata directly from
`config.id`, `.name`, `.bundleName`, and `.routes`. Routes are `RouteManifest`
values with full paths, parameter metadata, and `hasAccessHandler` flags.
The config owns `applicationAccessHandler`, `routeAccessHandlers`, and `htmlRenderer`;
the starter reads those dependencies from each config.
No authoring definition is referenced at runtime.

If the source definition has prefix `account` and declares
`route("users/{id}", "UserDetail")`, the generated config carries the full path
`/account/users/{id}`, and the starter registers:

```text
GET /account/users/{id}
```

The route only matches `GET`. Invalid requests use the recovery destination chosen
by the required `RouteFailureHandler` bean.

## Application Access

Every generated config requires its application-specific handler type.
Extend `AccountApplicationAccessHandler`, which derives from
`ApplicationAccessHandler<AccountApplicationConfig>`. The generic parameter
associates the handler with its config; no config instance is injected into it.
Supply the handler to the config's constructor.

The handler checks whether the current user can view the application. It can
use your authentication service and compose reusable checks through constructor
injection. For example, `AccountPermissions` below is an app-owned service;
`PublicRoutes.Login()` is a generated route in a separately configured public
application:

```kotlin
import com.example.generated.spa.routes.AccountApplicationAccessHandler
import com.example.generated.spa.routes.PublicRoutes
import com.sparouting.contract.AccessDecision
import com.sparouting.contract.RouteRequest
import org.springframework.stereotype.Component

@Component
class CheckAccountAccess(
  private val permissions: AccountPermissions
) : AccountApplicationAccessHandler() {
  override fun evaluate(request: RouteRequest): AccessDecision {
    if (!permissions.canViewApplication(request)) {
      return AccessDecision.Denied(
        destination = PublicRoutes.Login()
      )
    }
    return AccessDecision.Allowed
  }
}
```

Generated constructors reject missing handlers and handlers belonging to another
application at compile time. Spring still resolves your component dependencies;
missing or ambiguous dependencies fail during bean creation. Reusable permission
services can be injected into multiple handlers.

The starter constructs one private evaluator from the config beans and
shares it between page requests and navigation checks.

There are two access checks, after parameter validation:

1. The application handler decides whether the user can view the application.
   `AccessDecision.Denied` stops evaluation immediately.
2. If the application returns `AccessDecision.Allowed`, the matching route
   handler decides whether the user can view that route. Unflagged routes need
   no route handler and are allowed after the application check.

Both handlers return `AccessDecision.Allowed` or `AccessDecision.Denied`.
For a public application, register a handler that explicitly allows access:

```kotlin
@Component
class CheckBlogAccess : BlogApplicationAccessHandler() {
  override fun evaluate(request: RouteRequest): AccessDecision = AccessDecision.Allowed
}
```

There are no ordered rule lists, skip results, or implicit application allowance.
If the sign-in page belongs to the gated application itself, its application
handler must allow that route so a redirect does not loop.

## Generated Access Handlers

Opt a route into a required, typed handler in the shared definition:

```kotlin
route("posts/{postId}", "Post", generateAccessHandler = true)
```

`generateServerRoutes` emits `Post.kt`, `PostRequest.kt`, and `PostAccessHandler.kt`
in the application's generated route package. `PostRequest` exposes `postId:
String`; `PostAccessHandler` is a separate abstract class extending
`RouteAccessHandler<PostRequest>`. Its generated implementation supplies the route
identity and converts validated input into `PostRequest`.

Implement the handler and register it as a Spring bean. The blog example keeps
the handler in a framework-neutral module:

```kotlin
import com.sparouting.contract.AccessDecision
import com.sparouting.examples.blog.BlogPostService
import com.sparouting.examples.generated.routes.BlogRoutes
import com.sparouting.examples.generated.routes.blog.PostAccessHandler
import com.sparouting.examples.generated.routes.blog.PostRequest

class CheckPostAccess(private val posts: BlogPostService) : PostAccessHandler() {
  override fun evaluate(request: PostRequest): AccessDecision {
    if (posts.find(request.postId) == null) {
      return AccessDecision.Denied(
        destination = BlogRoutes.NotFound()
      )
    }
    return AccessDecision.Allowed
  }
}
```

Register the service and handlers in a Spring `@Configuration` class, then
wire the handlers into the generated collection and config:

```kotlin
@Bean
fun blogPostService(): BlogPostService = BlogPostService()

@Bean
fun checkBlogAccess(): CheckBlogAccess = CheckBlogAccess()

@Bean
fun checkPostAccess(posts: BlogPostService): CheckPostAccess = CheckPostAccess(posts)

@Bean
fun checkEditPostAccess(posts: BlogPostService): CheckEditPostAccess = CheckEditPostAccess(posts)

@Bean
fun blogRouteAccessHandlers(
  post: PostAccessHandler,
  editPost: EditPostAccessHandler
): BlogRouteAccessHandlers = BlogRouteAccessHandlers(post = post, editPost = editPost)

@Bean
fun blogConfig(
  applicationAccessHandler: CheckBlogAccess,
  routeAccessHandlers: BlogRouteAccessHandlers
): BlogApplicationConfig = BlogApplicationConfig(
  applicationAccessHandler = applicationAccessHandler,
  routeAccessHandlers = routeAccessHandlers,
  htmlRenderer = HtmlDocumentRenderer()
)
```

`BlogRouteAccessHandlers` implements `RouteAccessHandlers<BlogApplicationConfig>`.
Every gated route requires an argument of its generated handler type. Adding a
gated route makes existing wiring fail to compile until its handler is supplied.
Standalone handler beans are not registered automatically. For custom configs
or collections, runtime validation rejects missing, duplicate, unknown-route,
unflagged-route, and wrong-application handlers.

The flag defaults to `false`. Unflagged routes require no handler and are served
once the application handler allows access. Every app, including a public
app, must register that application handler.
Generated client routes expose this setting as `hasAccessHandler: boolean`:

```ts
BlogRoutes.Post.hasAccessHandler;  // true
BlogRoutes.Index.hasAccessHandler; // false
```

This describes route-specific checks. Application access is configured on the
server and is not included in this flag. If the application handler always
allows access, the client can skip the decision request when `hasAccessHandler`
is `false`. Applications that check application access on each navigation must
continue calling the endpoint for every route. Treat missing metadata as
requiring a check when integrating clients generated by an older version.

Both page requests and route decisions validate parameters before evaluating
the application gate, then the access handler.
`AccessDecision.Allowed` serves the route; `AccessDecision.Denied(destination)`
resolves a generated `RouteTarget`. The default converter maps a page denial to a
`302` redirect. The decision endpoint includes the type and destination in its
JSON response.

Request models expose path values directly, with optional values nullable.
For routes with declared query fields, `request.queryString` uses the route's
existing `QueryString` model, including nullable and repeated values.
`request.context` exposes headers (including a case-insensitive `header(name)`
helper), target method (`GET`), destination path resolved from route metadata, raw path
values, and all raw query-string values.
If a path parameter uses `queryString` or `context`, the generated metadata
property appends underscores until its name is unique. Generated access/request
type names must not collide with another route ID in the application.

The configuration, request, decision, and access handler contracts live in core.
Validation, registration, and execution live in the framework-neutral runtime.
The Spring starter discovers config beans and supplies request data to that runtime.

## Deny Navigation From Access Handlers

Either handler can deny navigation with a typed alternative destination:

```kotlin
import com.example.generated.spa.routes.AccountRoutes
import com.sparouting.contract.AccessDecision

AccessDecision.Denied(
  destination = AccountRoutes.UserDetail(id = "123")
)
```

Denial destinations are validated against the target route parameters. Unknown
applications, unknown routes, and invalid target parameters fail with clear
runtime errors instead of producing broken URLs.

The example above uses `route("users/{id}", "UserDetail")`. The `id` parameter is
inferred from the path as a string. Pass strings, including numeric IDs, to
generated Kotlin and TypeScript route builders. The runtime rejects missing
required path parameters and unknown path parameter names. Value-format
validation belongs in application code.

## Typed Query Strings

Add a separate query-string declaration list to a route:

```kotlin
route(
  "users/{id}/search",
  "UserSearch",
  queryString = listOf(
    parameter("q"),
    parameter("sort").optional(),
    parameter("tag").repeated().optional()
  )
)
```

The generated builders check field names, required arguments, and scalar versus
list values. Kotlin uses a nested `QueryString` data class; TypeScript exports a
`UserSearchQueryString` type. Routes without path parameters accept only the
`queryString` object. When all its fields are optional, the entire argument can
be omitted.

```ts
AccountRoutes.UserSearch({ id: "123" }, { q: "hello world", tag: ["a", "b"] });
// /account/users/123/search?q=hello+world&tag=a&tag=b
```

```kotlin
import com.example.generated.spa.routes.account.UserSearch
import com.sparouting.contract.AccessDecision

AccessDecision.Denied(
  destination = UserSearch(
    id = "123",
    queryString = UserSearch.QueryString(q = "hello world", tag = listOf("a", "b"))
  )
)
```

Kotlin targets retain query-string values in `RouteTarget.queryString` as
`Map<String, List<String>>`. The evaluator validates and URL-encodes
these values. Encoding preserves repeated-value order and uses `+` for spaces;
an omitted query string never adds a trailing `?`. Route `.path` metadata contains only
the path pattern.

Declared query-string fields follow these rules:

| Declaration | Incoming values | Generated argument |
| --- | --- | --- |
| `parameter("q")` | Exactly one | `String` / `string` |
| `parameter("q").optional()` | Zero or one | Nullable `String` / optional `string` |
| `parameter("tag").repeated()` | One or more | `List<String>` / `readonly string[]` |
| `parameter("tag").repeated().optional()` | Zero or more | Nullable list / optional array |

`.optional()` and `.repeated()` may be applied in either order. Repeated
declarations are only valid for query strings. Empty strings count as present
values; empty optional lists and omitted optional fields add no key. Builders
reject empty required lists at runtime. Path and query-string keys may share a
name. Duplicate names and colliding generated identifiers are rejected.

Validation runs before application and route access handlers on both page loads and route
decisions. Invalid query strings use the configured recovery destination;
invalid typed denial or recovery destinations throw `IllegalArgumentException`.
Extra incoming keys such as `utm_source` are accepted and remain in the raw
request map. Routes without declarations keep accepting arbitrary query strings.

Use the generated enum map helpers to read declared values:

```kotlin
val queryString = UserSearch.queryString(request.queryString)
val search = queryString[UserSearch.QueryStringKey.Q]?.firstOrNull()
val tags = queryString[UserSearch.QueryStringKey.TAG].orEmpty()
val campaign = request.queryStringValue("utm_source")
```

```ts
import { AccountRoutes, UserSearchQueryStringKey } from "./__generated__/routes/AccountRoutes";

const raw = new URLSearchParams(location.search);
const queryString = AccountRoutes.UserSearch.queryString(raw);
const search = queryString[UserSearchQueryStringKey.Q]?.[0];
const tags = queryString[UserSearchQueryStringKey.TAG] ?? [];
const campaign = raw.get("utm_source");
```

The Kotlin helper returns `Map<UserSearch.QueryStringKey, List<String>>`. The TypeScript
helper returns a readonly partial record keyed by `UserSearchQueryStringKey`, whose
values are readonly string arrays. Both helpers omit absent and undeclared keys,
preserve all values, and leave the raw input unchanged. They do not validate or
construct the generated `QueryString` model. Kotlin enum entries expose the URL name
through `wireName`; TypeScript string enums use the URL name as their value.

## Render HTML

Supply a renderer when constructing the generated config. The runtime's
`HtmlDocumentRenderer` builds the default shell with a root element and bundle
script. Its options control bundle paths and stylesheets:

```kotlin
import com.sparouting.runtime.rendering.HtmlDocumentRenderer

val renderer = HtmlDocumentRenderer(
  bundleBasePath = "/bundles",
  includeRouteStylesheet = true,
  globalStylesheet = "/bundles/stylex.css"
)
```

For custom HTML, implement the core `HtmlRenderer` interface or use a lambda:

```kotlin
import com.sparouting.contract.HtmlRenderer

val config = AccountApplicationConfig(
  applicationAccessHandler = checkAccountAccess,
  routeAccessHandlers = AccountRouteAccessHandlers(),
  htmlRenderer = HtmlRenderer { application -> MyPage(application).render() }
)
```

The renderer returns an HTML string. The adapter writes a `200 text/html`
response only after access is allowed. Redirects, invalid requests, and navigation
decisions skip rendering. Each application can use a different renderer; pass
the same instance to multiple configs when they share a shell. A renderer can
be injected into your config factory like any other application dependency.

## Configure Properties

```yaml
spa-routing:
  server:
    enabled: true
  route-decision:
    enabled: true
    path: /__spa/route-decision
```

Set `spa-routing.server.enabled=false` when the application only wants the
route decision endpoint and does not want the starter to register MVC SPA
routes. Set `spa-routing.route-decision.enabled=false` when you do not want the
starter to register the built-in decision endpoint.

## Use Route Decisions From The Client

The starter registers `GET /__spa/route-decision` by default. Use it before a
client-side route change when the client needs the same allow or deny
decision that a full page load would receive.

```http
GET /__spa/route-decision?applicationId=account&routeId=UserDetail&parameters.id=123&queryString.tab=billing
```

The endpoint always responds with HTTP `200` when the decision request itself is
valid. The route decision is in the JSON body:

```json
{
  "type": "denied",
  "destination": "/login"
}
```

Response bodies use this shape:

```ts
type RouteDecision =
  | { type: "allowed" }
  | {
      type: "denied" | "unknown_route" | "invalid_request";
      destination: string;
    };
```

`Cache-Control: no-store` is applied because route decisions commonly depend on
the current authenticated user. The endpoint evaluates access using the real
request headers, cookies, and security context from the decision request; clients
do not pass headers as query parameters. Route parameters use the `parameters.`
query parameter prefix, for example `parameters.id=123`. Target route query
parameters use the `queryString.` prefix, for example
`queryString.tab=billing`.

Pass repeated query values as repeated prefixed keys. For the declared
`UserSearch` route above:

```http
GET /__spa/route-decision?applicationId=account&routeId=UserSearch&parameters.id=123&queryString.q=hello&queryString.tag=a&queryString.tag=b
```

The decision endpoint builds the same `RouteRequest` type used for page loads:

- `applicationId`: from the `applicationId` query parameter
- `routeId`: from the `routeId` query parameter
- `pathParameters`: values from `parameters.*`
- `queryString`: values from `queryString.*`
- `headers`: real request headers from the decision request

The endpoint does not call `RouteRequestFactory`; that factory adapts page-load
`ServerRequest` instances into the shared request type. Both endpoints forward
their actual incoming headers. Application handlers receive the fields above.
Typed route handlers also receive a `RouteAccessContext` with method `GET` and
the destination path resolved from route metadata for both entry points.

Both endpoints call the same `RouteRequestEvaluator.evaluate(RouteRequest)`. For page loads,
`RouteRequestFactory` runs before shared validation; validation applies to the
values it returns before either access handler executes.

The generated TypeScript route files do not include a route decision helper, but
each generated route builder carries its `applicationId` and `routeId`, so
app-specific navigation behavior can read them instead of hardcoding strings:

```ts
import { AccountRoutes } from "./__generated__/routes/AccountRoutes";

type RouteDecision =
  | { type: "allowed" }
  | {
      type: "denied" | "unknown_route" | "invalid_request";
      destination: string;
    };

async function decideRoute(
  route: { applicationId: string; routeId: string },
  parameters: Record<string, string> = {},
  queryString: Record<string, readonly string[]> = {}
): Promise<RouteDecision> {
  const decisionParams = new URLSearchParams({
    applicationId: route.applicationId,
    routeId: route.routeId,
  });

  Object.entries(parameters).forEach(([name, value]) => {
    decisionParams.set(`parameters.${name}`, value);
  });

  Object.entries(queryString).forEach(([name, values]) => {
    values.forEach(value => decisionParams.append(`queryString.${name}`, value));
  });

  const response = await fetch(`/__spa/route-decision?${decisionParams}`);
  return (await response.json()) as RouteDecision;
}

// decideRoute(AccountRoutes.UserDetail, { id: "123" });
// decideRoute(AccountRoutes.UserSearch, { id: "123" }, { q: ["hello"], tag: ["a", "b"] });
```

Continue navigation for `allowed`; otherwise navigate to the returned `destination`.
All three failure types carry a required destination. The default page
converter renders allowed pages and redirects failures to that same destination.
The destination page owns any user-facing explanation. The decision endpoint
returns no embedded HTTP status, `location`, or `reason` field.

For custom GraphQL or REST APIs, construct a `RouteRequestEvaluator` from your
configs. The starter's evaluator is private and is not exposed as a Spring bean:

```kotlin
import com.sparouting.contract.RouteRequest
import com.sparouting.contract.SinglePageApplicationConfig
import com.sparouting.runtime.evaluation.RouteRequestEvaluator
import com.sparouting.runtime.evaluation.RouteFailureHandler

class RouteDecisionHandler(
  configs: List<SinglePageApplicationConfig>,
  failureHandler: RouteFailureHandler
) {
  private val evaluator = RouteRequestEvaluator(configs, failureHandler)

  fun evaluateAccountRoute(
    routeId: String,
    parameters: Map<String, String>,
    queryString: Map<String, List<String>>,
    headers: Map<String, List<String>>
  ) = evaluator.evaluate(
    RouteRequest(
      applicationId = "account",
      routeId = routeId,
      pathParameters = parameters,
      queryString = queryString,
      headers = headers
    )
  )
}
```

The custom handler above returns `RouteResult`. Map that result to your API's response format;
the built-in navigation endpoint serializes the result directly.

## Replace Starter Beans

Define your own bean when the defaults are not enough:

```kotlin
import com.sparouting.spring.request.RouteRequestFactory
import com.sparouting.runtime.response.RouteHttpResponseConverter
import org.springframework.context.annotation.Bean

@Bean
fun routeRequestFactory(): RouteRequestFactory = MyRouteRequestFactory()

@Bean
fun routeHttpResponseConverter(): RouteHttpResponseConverter = MyRouteHttpResponseConverter()
```

`RouteRequestFactory` is the replaceable page request converter. A
`RouteHttpResponseConverter` bean replaces page HTTP mapping only; navigation JSON
always exposes the semantic result. The converter receives the original request and
resolved result. See [HTTP conversion](runtime.md#customize-http-conversion).

Recovery destinations belong in `RouteFailureHandler`, which runs for
both entry points. The default HTTP converter renders allowed pages and sends every
failure to its resolved destination with a `302`. Only `Allowed` mapped to `200`
without a location renders HTML.

## Migration Checklist

For an existing Spring app that copied SPA routing code locally:

1. Add `spa-routing-spring-boot-starter`.
2. Keep `SinglePageApplicationDefinition` objects in the code-generation project and regenerate server routes/configs. Remove the definitions project from the server's implementation dependencies.
3. Replace application rule lists with an implementation of each generated application access handler. Move reusable checks into its dependencies; use `AccessDecision` at both access levels.
4. Replace copied registry, evaluator, request adapter, and response classes with the starter.
5. Construct the generated route-handler collection with every gated handler, and pass it, the application handler, and a core `HtmlRenderer` to the generated `<ApplicationName>ApplicationConfig`. Expose that instance as a bean.
6. Use `HtmlDocumentRenderer(...)` for the default shell or supply your own renderer returning an HTML string.
7. Supply a `RouteFailureHandler` bean with typed fallback destinations for unknown routes and invalid requests.
8. Call the built-in route decision endpoint from client navigation guards, or keep using `RouteRequestEvaluator` from a custom GraphQL or REST endpoint.
