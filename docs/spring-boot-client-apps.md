# Spring Boot Client Apps

This guide is for Spring Boot applications that want to serve SPA routes using
`spa-routing`.

Use the starter when your app already has `SinglePageApplicationDefinition` objects and
you want Spring to handle:

- registering MVC `GET` routes for each SPA route
- validating path parameters
- evaluating application and route access handlers
- resolving typed redirects
- rendering a default SPA HTML page
- exposing a route decision endpoint for client-side navigation checks

Your application still owns:

- route definitions
- `SinglePageApplicationConfig` beans
- app-specific access handlers
- custom HTML rendering, if the default page is not enough
- any custom GraphQL or REST route decision endpoint, if you do not want the built-in endpoint

## Add Dependencies

Add the Spring Boot starter to the Spring application that will serve the SPA
routes:

```kotlin
dependencies {
  implementation("io.github.caseymcguire:spa-routing-spring-boot-starter:0.3.0")
  implementation(project(":spa-route-definitions"))
}
```

The `:spa-route-definitions` dependency is the project where your concrete
`SinglePageApplicationDefinition` objects live.

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

Packages below share the prefix `io.github.caseymcguire.sparouting`:

- `runtime.config`: application configuration, validation, and route registry
- `runtime.access`: application access handler contract, two-level access evaluation, and route handler registration validation
- `runtime.request`: framework-neutral request model
- `runtime.response`: route-decision request, response, and shared evaluation service
- `runtime.rendering`: HTML document builder and asset options
- `spring.config`: `SpringSinglePageApplicationConfig` with the optional `renderHtml()` override
- `spring.request`: Spring request factory
- `spring.response`: Spring response conversion
- `spring.rendering`: Spring HTML renderer interface and default adapter
- `spring.web`: Spring MVC router factories
- `spring.autoconfigure`: Spring Boot properties and auto-configuration

If the same Spring app also generates server route objects for typed access handlers
or typed redirects, apply and configure the Gradle plugin too:

```kotlin
plugins {
  id("io.github.caseymcguire.spa-routing") version "0.3.0"
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

For each SPA that should be served, expose a `SinglePageApplicationConfig` bean.
The starter reads these beans during auto-configuration.

```kotlin
package com.example.web

import com.example.routes.AccountApplication
import com.sparouting.contract.AccessDecision
import io.github.caseymcguire.sparouting.runtime.access.ApplicationAccessHandler
import io.github.caseymcguire.sparouting.runtime.config.SinglePageApplicationConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RoutesConfiguration {
  @Bean
  fun accountConfig(): SinglePageApplicationConfig {
    return object : SinglePageApplicationConfig {
      override val application = AccountApplication
      override val accessHandler = ApplicationAccessHandler { AccessDecision.Allow }
    }
  }
}
```

If `AccountApplication.urlPrefix` is `account` and it defines
`route("users/{id}", "UserDetail")`, the starter registers:

```text
GET /account/users/{id}
```

The route only matches `GET`. Invalid path parameter values return
`spa-routing.server.invalid-path-parameter-status`, which defaults to `400`.

## Application Access

Every application config must supply one `ApplicationAccessHandler`. It checks
whether the current user can view that application. The handler can use your
existing authentication service and compose reusable checks through constructor
injection. For example, `AccountPermissions` below is an app-owned service;
`PublicRoutes.Login()` is a generated route in a separately configured public
application:

```kotlin
import com.example.generated.spa.routes.PublicRoutes
import com.sparouting.contract.AccessDecision
import io.github.caseymcguire.sparouting.runtime.access.ApplicationAccessHandler
import io.github.caseymcguire.sparouting.runtime.config.SinglePageApplicationConfig
import io.github.caseymcguire.sparouting.runtime.request.RouteRequest
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component

@Component
class CheckAccountAccess(private val permissions: AccountPermissions) : ApplicationAccessHandler {
  override fun evaluate(request: RouteRequest): AccessDecision {
    if (!permissions.canViewApplication(request)) {
      return AccessDecision.Redirect(PublicRoutes.Login())
    }
    return AccessDecision.Allow
  }
}

@Bean
fun accountConfig(checkAccountAccess: CheckAccountAccess): SinglePageApplicationConfig {
  return object : SinglePageApplicationConfig {
    override val application = AccountApplication
    override val accessHandler = checkAccountAccess
  }
}
```

The config binds a specific application to its handler. Spring injects that
component normally; the library does not select an application handler from
an unqualified list of beans. Multiple applications can supply different
handlers, or explicitly share one.

There are two access checks, after parameter validation:

1. The application handler decides whether the user can view the application.
   `AccessDecision.Redirect` stops evaluation immediately.
2. If the application returns `AccessDecision.Allow`, the matching route
   handler decides whether the user can view that route. Unflagged routes need
   no route handler and are allowed after the application check.

Both handlers return `AccessDecision.Allow` or `AccessDecision.Redirect`.
There are no ordered rule lists, skip results, or implicit application allowance.
For a public application, use `ApplicationAccessHandler { AccessDecision.Allow }`.
An omitted `accessHandler` is a compile error when implementing the config.
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

Implement the handler as a Spring bean. For example, the blog example uses:

```kotlin
import com.sparouting.contract.AccessDecision
import com.sparouting.examples.generated.routes.BlogRoutes
import com.sparouting.examples.generated.routes.blog.PostAccessHandler
import com.sparouting.examples.generated.routes.blog.PostRequest
import org.springframework.stereotype.Component

@Component
class CheckPostAccess(private val posts: BlogPostStore) : PostAccessHandler() {
  override fun evaluate(request: PostRequest): AccessDecision {
    if (posts.find(request.postId) == null) {
      return AccessDecision.Redirect(BlogRoutes.NotFound())
    }
    return AccessDecision.Allow
  }
}
```

The starter collects `RouteAccessHandler<*>` beans automatically. Exactly one
handler must be registered for every enabled route. Missing or duplicate
handlers fail startup, as do handlers for unknown routes or routes without
`generateAccessHandler = true`.

The flag defaults to `false`. Unflagged routes require no handler and are served
once the application handler allows access. Every app, including a public
app, must provide that application handler.
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
`AccessDecision.Allow` serves the route; `AccessDecision.Redirect(target)`
resolves a generated `RouteTarget` and produces a `302` redirect. The decision
endpoint reports that redirect through its existing JSON response.

Request models expose path values directly, with optional values nullable.
For routes with declared query fields, `request.queryString` uses the route's
existing `QueryString` model, including nullable and repeated values.
`request.context` exposes headers (including a case-insensitive `header(name)`
helper), method, path, raw path values, and all raw query-string values.
If a path parameter uses `queryString` or `context`, the generated metadata
property appends underscores until its name is unique. Generated access/request
type names must not collide with another route ID in the application.

The shared decision and typed route access contracts live in core. The
application handler contract, validation, registration, and execution live
in the framework-neutral runtime. The Spring starter collects handler beans
and supplies request data to that runtime.

## Redirect From Access Handlers

Either handler can redirect to a typed generated route target:

```kotlin
import com.example.generated.spa.routes.AccountRoutes
import com.sparouting.contract.AccessDecision

AccessDecision.Redirect(AccountRoutes.UserDetail(id = "123"))
```

Typed redirects are validated against the target route parameters. Unknown
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

AccessDecision.Redirect(
  UserSearch(id = "123", queryString = UserSearch.QueryString(q = "hello world", tag = listOf("a", "b")))
)
```

Kotlin targets retain query-string values in `RouteTarget.queryString` as
`Map<String, List<String>>`. The response service validates and URL-encodes
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
decisions. Invalid query strings use `spa-routing.server.invalid-query-string-status`
(default `400`); invalid typed redirect targets throw `IllegalArgumentException`.
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

By default, the starter renders a small HTML page:

```html
<div id="root"></div>
<script type="module" src="/bundles/{bundleName}.bundle.js"></script>
```

It can also include route CSS and a global stylesheet through properties.

Override rendering for one SPA by implementing `SpringSinglePageApplicationConfig`:

```kotlin
import com.sparouting.contract.AccessDecision
import io.github.caseymcguire.sparouting.runtime.access.ApplicationAccessHandler
import io.github.caseymcguire.sparouting.spring.config.SpringSinglePageApplicationConfig
import org.springframework.http.MediaType
import org.springframework.context.annotation.Bean
import org.springframework.web.servlet.function.ServerResponse

@Bean
fun accountConfig(): SpringSinglePageApplicationConfig {
  return object : SpringSinglePageApplicationConfig {
    override val application = AccountApplication
    override val accessHandler = ApplicationAccessHandler { AccessDecision.Allow }

    override fun renderHtml(): ServerResponse? {
      return ServerResponse.ok()
        .contentType(MediaType.TEXT_HTML)
        .body(AccountPage().render())
    }
  }
}
```

The Spring-specific interface extends `SinglePageApplicationConfig`, so the
starter discovers it through the same bean registration. Its rendering hook
runs only after access is allowed; returning `null` uses the `HtmlRenderer` bean.
The shared configuration interface has no HTTP rendering methods.

Override rendering for all SPAs by replacing the `HtmlRenderer` bean:

```kotlin
import io.github.caseymcguire.sparouting.runtime.config.SinglePageApplicationConfig
import io.github.caseymcguire.sparouting.spring.rendering.HtmlRenderer
import org.springframework.context.annotation.Bean
import org.springframework.web.servlet.function.ServerResponse

@Bean
fun htmlRenderer(): HtmlRenderer {
  return object : HtmlRenderer {
    override fun render(application: SinglePageApplicationConfig): ServerResponse {
      return ServerResponse.ok().body(MyPage(application).render())
    }
  }
}
```

## Configure Properties

```yaml
spa-routing:
  server:
    enabled: true
    invalid-path-parameter-status: 400
    invalid-query-string-status: 400
  route-decision:
    enabled: true
    path: /__spa/route-decision
  assets:
    bundle-base-path: /bundles
    include-route-stylesheet: true
    global-stylesheet: /bundles/stylex.css
```

Set `spa-routing.server.enabled=false` when the application only wants the
route decision endpoint and does not want the starter to register MVC SPA
routes. Set `spa-routing.route-decision.enabled=false` when you do not want the
starter to register the built-in decision endpoint.

## Use Route Decisions From The Client

The starter registers `GET /__spa/route-decision` by default. Use it before a
client-side route change when the client needs the same allow, deny, or redirect
decision that a full page load would receive.

```http
GET /__spa/route-decision?applicationId=account&routeId=UserDetail&parameters.id=123&queryString.tab=billing
```

The endpoint always responds with HTTP `200` when the decision request itself is
valid. The route decision is in the JSON body:

```json
{
  "statusCode": 302,
  "location": "/login"
}
```

Response bodies use this shape:

```ts
type RouteDecision = {
  statusCode: number;
  location?: string | null;
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

The decision endpoint builds a synthetic `RouteRequest` for the target route:

- `applicationId`: from the `applicationId` query parameter
- `routeId`: from the `routeId` query parameter
- `method`: always `GET`
- `path`: the resolved target route path
- `pathParameters`: values from `parameters.*`
- `queryString`: values from `queryString.*`
- `headers`: real request headers from the decision request

The endpoint does not call `RouteRequestFactory`; that factory adapts real
page-load `ServerRequest` instances. Application handlers should rely on the fields
above, or the application should replace `RouteResponseService` for a custom
decision context.

Both endpoints now use `RouteResponseService`: page loads call
`evaluate(RouteRequest)` and navigation checks call `evaluate(RouteResponseRequest)`.
A custom service should account for both overloads. For page loads,
`RouteRequestFactory` runs before shared validation; validation applies to the
values it returns before either access handler executes.

The generated TypeScript route files do not include a route decision helper, but
each generated route builder carries its `applicationId` and `routeId`, so
app-specific navigation behavior can read them instead of hardcoding strings:

```ts
import { AccountRoutes } from "./__generated__/routes/AccountRoutes";

type RouteDecision = {
  statusCode: number;
  location?: string | null;
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

Decision statuses match what the MVC route would use:

- `200`: navigation is allowed
- `302` with `location`: redirect
- configured `spa-routing.server.invalid-path-parameter-status`: invalid path parameters
- configured `spa-routing.server.invalid-query-string-status`: invalid declared query-string values
- `404`: unknown route

For custom GraphQL or REST APIs, call `RouteResponseService` directly:

```kotlin
import io.github.caseymcguire.sparouting.runtime.response.RouteResponseRequest
import io.github.caseymcguire.sparouting.runtime.response.RouteResponseService

class RouteDecisionHandler(
  private val routeResponseService: RouteResponseService
) {
  fun evaluateAccountRoute(
    routeId: String,
    parameters: Map<String, String>,
    queryString: Map<String, List<String>>,
    headers: Map<String, List<String>>
  ) = routeResponseService.evaluate(
    RouteResponseRequest(
      applicationId = "account",
      routeId = routeId,
      parameters = parameters,
      queryString = queryString,
      headers = headers
    )
  )
}
```

## Replace Starter Beans

Define your own bean when the defaults are not enough:

```kotlin
import io.github.caseymcguire.sparouting.spring.request.RouteRequestFactory
import org.springframework.context.annotation.Bean

@Bean
fun routeRequestFactory(): RouteRequestFactory = MyRouteRequestFactory()
```

Replaceable beans:

- `SinglePageApplicationRouteRegistry`
- `RouteAccessEvaluator`
- `RouteRequestFactory`
- `HtmlRenderer`
- `RouteResponseService`

## Migration Checklist

For an existing Spring app that copied SPA routing code locally:

1. Add `spa-routing-spring-boot-starter`.
2. Keep app-owned `SinglePageApplicationDefinition` objects in the route definitions project.
3. Replace application rule lists with one `ApplicationAccessHandler` per config. Move reusable checks into its dependencies; use `AccessDecision` at both access levels.
4. Replace copied registry, evaluator, request adapter, and response classes with the starter.
5. Expose one `SinglePageApplicationConfig` bean per SPA, with its required `accessHandler`.
6. Move any app-specific HTML page rendering into `SpringSinglePageApplicationConfig.renderHtml()` or a `HtmlRenderer` bean.
7. Call the built-in route decision endpoint from client navigation guards, or keep using `RouteResponseService` from a custom GraphQL or REST endpoint.
