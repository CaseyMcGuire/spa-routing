# spa-routing

`spa-routing` lets a Kotlin server and a TypeScript SPA share one route definition source. You define your SPA routes once in Kotlin, then generate:

- typed TypeScript route builders for the client
- typed Kotlin route objects for the server
- bundle entry metadata for webpack or Vite

## Runnable Examples

The [examples](examples/README.md) include a Spring Boot application using the
local library projects and shared Kotlin route definitions:

```sh
./gradlew :examples:spring:run
```

Then open [http://localhost:8080/](http://localhost:8080/).

## Install

For Gradle route generation:

```kotlin
plugins {
  id("io.github.caseymcguire.spa-routing") version "0.3.0"
}

dependencies {
  implementation("io.github.caseymcguire:spa-routing-core:0.3.0")
  implementation(project(":spa-route-definitions"))
}
```

The `:spa-route-definitions` project is your app-owned module containing concrete `SinglePageApplicationDefinition` objects. The plugin needs that project on the generator classpath because route discovery loads those objects at runtime.

For Spring Boot route serving:

```kotlin
dependencies {
  implementation("io.github.caseymcguire:spa-routing-spring-boot-starter:0.3.0")
  implementation(project(":spa-route-definitions"))
}
```

The starter targets Spring Boot 4.x and brings in `spring-boot-starter-web`.
Use it from Spring Boot 4 applications.

## Define Routes

Route definitions must live in a module separate from the one the plugin is applied to. The plugin compiles the generated server routes into the plugin's module, but generating them first needs the route definitions compiled, so keeping both in one module creates a `compileKotlin -> generateServerRoutes -> classes -> compileKotlin` cycle. Put your concrete `SinglePageApplicationDefinition` objects in a dedicated module, commonly under:

```txt
spa-route-definitions/src/main/kotlin/com/sparouting/contract/applications
```

Example:

```kotlin
package com.sparouting.contract.applications

import com.sparouting.contract.SinglePageApplicationDefinition
import com.sparouting.contract.route

object AccountApplication : SinglePageApplicationDefinition {
  override val id = "account"
  override val name = "Account"
  override val urlPrefix = "account"
  override val appRootPath = "src/main/web-frontend/apps/account"
  override val routes = listOf(
    route("settings", "Settings"),
    route("users/{id}", "UserDetail")
  )
}
```

Path parameters are inferred from `{placeholders}` and generate Kotlin `String`
and TypeScript `string` values, including numeric IDs. No separate declaration
is needed for `id` in the example above. Explicit `parameters` metadata remains
available for optional path values via `parameter("name").optional()`. The runtime
checks required parameters and rejects unknown names; value-format validation
belongs in application code.

## Typed Query Strings

Declare query-string fields separately from path parameters:

```kotlin
import com.sparouting.contract.parameter

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

Generated TypeScript builders accept a separate `queryString` object:

```ts
AccountRoutes.UserSearch({ id: "123" }, { q: "hello world", tag: ["a", "b"] });
// /account/users/123/search?q=hello+world&tag=a&tag=b
```

Generated Kotlin routes accept a nested `QueryString` value:

```kotlin
import com.example.generated.spa.routes.account.UserSearch

val target = UserSearch(
  id = "123",
  queryString = UserSearch.QueryString(q = "hello world", tag = listOf("a", "b"))
)
```

Required fields must be supplied. Optional fields may be omitted; repeated fields
take string lists. Routes without path parameters accept just the `queryString`
object. When all its fields are optional, that object can be omitted. Generated
`.path` metadata remains the path pattern without a query string.

Each route with query-string declarations also generates an enum and a map helper:

```kotlin
val queryString = UserSearch.queryString(request.queryString)
val search = queryString[UserSearch.QueryStringKey.Q]?.firstOrNull()
```

```ts
import { AccountRoutes, UserSearchQueryStringKey } from "./__generated__/routes/AccountRoutes";

const queryString = AccountRoutes.UserSearch.queryString(new URLSearchParams(location.search));
const search = queryString[UserSearchQueryStringKey.Q]?.[0];
```

The runtime validates declared query-string fields, while preserving extra incoming
keys in the raw request map. See [the runtime guide](docs/spring-boot-client-apps.md#typed-query-strings)
for validation rules, redirects, and route decisions.

Generated route objects also expose `parse(params, searchParams)` for incoming
requests. It returns typed `{ params, queryString }` values, or `null` for missing
required values or invalid query-string cardinality. Path values should already be
decoded by the router; pass the query string as `URLSearchParams`.

For example, a React Router 7 data loader can use:

```ts
const parsed = AccountRoutes.UserSearch.parse(params, new URL(request.url).searchParams);
if (parsed === null) {
  throw new Response("Invalid route parameters", { status: 400 });
}

parsed.params.id;        // string
parsed.queryString.q;    // string
parsed.queryString.sort; // string | undefined
parsed.queryString.tag;  // readonly string[] | undefined
```

Optional values that are absent are omitted. Empty strings are valid, repeated
query-string values preserve their order, and undeclared keys are ignored.
URL builders and enum-keyed query-string helpers remain available.

Every generated client route also exposes `hasAccessHandler: boolean`, derived
from its `generateAccessHandler` setting. For example,
`BlogRoutes.Post.hasAccessHandler` is `true`, while
`BlogRoutes.Index.hasAccessHandler` is `false`. A client router can use it to
request a route decision only for routes with extra checks when the application
gate is public. Application-wide rules are configured separately on the server;
apps that need those rules checked on each navigation must still call the endpoint.

A consumer-defined `createSpaRouter` can infer each `render(params, queryString)`
callback's arguments from the route's parser return type:

```ts
type ViewData = NonNullable<ReturnType<typeof WikiRoutes.View.parse>>;
// ViewData["params"] is { wikiId: string }
// ViewData["queryString"] is { tab?: string } when tab is declared optional
```

## Configure Generation

Add this to the consuming app's `build.gradle.kts`:

```kotlin
spaRouting {
  routeDefinitions {
    projectPath = ":spa-route-definitions"
  }

  clientRoutes {
    outputDirectory = "src/main/web-frontend/__generated__/routes"
  }

  serverRoutes {
    packageName = "com.example.generated.spa.routes"
    sourceRoot = "build/generated/source/spaRoutes/main"
  }

  bundleEntries {
    outputFile = "SinglePageApplicationBundles.ts"
  }
}
```

The bundle entries file is bundler-neutral: webpack consumes it as `entry`, and
Vite as `build.rollupOptions.input`. See
[docs/spring-boot-client-apps.md](docs/spring-boot-client-apps.md) for both setups.

If your route definitions live somewhere else, override the default source directory:

```kotlin
routeDefinitions {
  projectPath = ":spa-route-definitions"
  sourceDirectory = "src/main/kotlin/com/example/routes"
}
```

## Generated Tasks

The plugin adds:

- `generateClientRoutes`
- `generateServerRoutes`
- `generateBundleEntries`

Run all three manually:

```sh
./gradlew generateClientRoutes generateServerRoutes generateBundleEntries
```

When `org.jetbrains.kotlin.jvm` is applied, `generateServerRoutes` is wired into Kotlin compilation and `serverRoutes.sourceRoot` is added as a generated source root.

## Defaults

- `routeDefinitions.sourceDirectory`: `src/main/kotlin/com/sparouting/contract/applications`
- `serverRoutes.packageName`: `com.sparouting.generated.spa.routes`
- `serverRoutes.sourceRoot`: `build/generated/source/spaRoutes/main`
- server route output directory: derived from `serverRoutes.sourceRoot` and the configured package name

The client routes output directory and bundle entries output file are required because they are application-specific.

## Troubleshooting

If a generator task fails with `spaRouting.<name> must be set`, the plugin is missing required configuration for that task.

If discovery fails to find or load route definitions, check that:

- `routeDefinitions.projectPath` points to the module with your concrete `SinglePageApplicationDefinition` objects
- that module applies the Java or Kotlin JVM plugin
- `routeDefinitions.sourceDirectory` points at the Kotlin source directory containing those objects

Do not point generation only at `spa-routing-core`; the generators need the compiled app-specific route definition classes too.

## Spring Boot Runtime

The Spring Boot starter serves configured SPA routes from app-provided `SinglePageApplicationConfig` beans. Application code owns the configs and rules; the starter owns the registry, route matching, rule evaluation, redirects, the route decision endpoint, and default HTML response.

For complete client setup, access handlers, HTML rendering, properties, and route decision examples, see [docs/spring-boot-client-apps.md](docs/spring-boot-client-apps.md).

```kotlin
import io.github.caseymcguire.sparouting.spring.config.SinglePageApplicationConfig
import io.github.caseymcguire.sparouting.spring.rules.builtin.AllowAll
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RoutesConfiguration {
  @Bean
  fun accountConfig(): SinglePageApplicationConfig {
    return object : SinglePageApplicationConfig {
      override val application = AccountApplication
      override val rules = listOf(AllowAll())
    }
  }
}
```

Application-wide rules are a deny-by-default gate: an SPA whose rules all skip
or that has none answers `404`. Use the built-in `AllowAll` for a public SPA.
Once the gate passes, a registered route access handler returns `Allow` to
serve the route or `Redirect` to send the user elsewhere. Routes without an
access handler are served once the gate passes. See
[docs/spring-boot-client-apps.md](docs/spring-boot-client-apps.md) for details.

Add application-wide rules when every route in an SPA needs the same behavior:

```kotlin
import io.github.caseymcguire.sparouting.spring.request.RouteRequest
import io.github.caseymcguire.sparouting.spring.rules.RouteRule
import io.github.caseymcguire.sparouting.spring.rules.RouteRuleAction
import io.github.caseymcguire.sparouting.spring.rules.RouteRuleResult

class RequireLogin : RouteRule {
  override fun evaluate(request: RouteRequest): RouteRuleResult {
    return if (request.header("X-User").isEmpty()) {
      RouteRuleResult.Deny(RouteRuleAction.redirect("/login"))
    } else {
      RouteRuleResult.Allow
    }
  }
}
```

Attach application-wide rules from a config bean:

```kotlin
import io.github.caseymcguire.sparouting.spring.config.SinglePageApplicationConfig
import org.springframework.context.annotation.Bean

@Bean
fun accountConfig(): SinglePageApplicationConfig {
  return object : SinglePageApplicationConfig {
    override val application = AccountApplication
    override val rules = listOf(RequireLogin())
  }
}
```

For an automatically registered, typed handler, declare the route with
`generateAccessHandler = true` and extend its generated `<Route>AccessHandler` class
in a Spring `@Component`. The generated `<Route>Request` provides typed path
and query-string values. The handler returns `RouteDecision.Allow` or
`RouteDecision.Redirect(target)`. Spring registers and invokes the handler automatically. See
[generated access handlers](docs/spring-boot-client-apps.md#generated-access-handlers)
and the [blog example](examples/README.md).

Redirect to a raw URL or a generated typed SPA route:

```kotlin
import com.example.generated.spa.routes.AccountRoutes
import io.github.caseymcguire.sparouting.spring.rules.RouteRuleAction
import io.github.caseymcguire.sparouting.spring.rules.RouteRuleResult

RouteRuleResult.Deny(RouteRuleAction.redirect("/login"))

RouteRuleResult.Deny(
  RouteRuleAction.redirectTo(AccountRoutes.UserDetail(id = "123"))
)
```

Override the default HTML page for one SPA:

```kotlin
import io.github.caseymcguire.sparouting.spring.config.SinglePageApplicationConfig
import org.springframework.context.annotation.Bean
import org.springframework.http.MediaType
import org.springframework.web.servlet.function.ServerResponse

@Bean
fun accountConfig(): SinglePageApplicationConfig {
  return object : SinglePageApplicationConfig {
    override val application = AccountApplication

    override fun renderHtml(): ServerResponse? {
      return ServerResponse.ok()
        .contentType(MediaType.TEXT_HTML)
        .body(AccountPage().render())
    }
  }
}
```

Check a client-side navigation before changing routes. Each generated route
builder carries its `applicationId` and `routeId`, so the decision call does not
hardcode them:

```ts
import { AccountRoutes } from "./__generated__/routes/AccountRoutes";

const route = AccountRoutes.UserDetail;
const params = new URLSearchParams({
  applicationId: route.applicationId,
  routeId: route.routeId,
  "parameters.id": "123",
  "queryString.tab": "billing",
});

const response = await fetch(`/__spa/route-decision?${params}`);
const decision = await response.json() as {
  statusCode: number;
  location?: string | null;
};
```

Useful Spring properties:

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

Override these beans to customize runtime behavior:

- `HtmlRenderer`
- `RouteRuleActionResolver`
- `RouteResponseEvaluator`
- `RouteRequestFactory`
- `SinglePageApplicationRouteRegistry`
- `RouteResponseService`

## Development

Development requires JDK 21 and Node.js with npm on `PATH`. The build installs a
pinned TypeScript compiler into `spa-routing-core/build/typescript-tests` and
compiles and runs generated Kotlin and TypeScript API fixtures, including
negative type checks. These tools are test dependencies only.

Build and test:

```sh
./gradlew clean build
```

Publish locally:

```sh
./gradlew publishToMavenLocal
```

Release publishing instructions are in [docs/publishing.md](docs/publishing.md).
