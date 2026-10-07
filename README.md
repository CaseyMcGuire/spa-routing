# spa-routing

`spa-routing` lets a Kotlin server and a TypeScript SPA share one route definition source. You define your SPA routes once in Kotlin, then generate:

- typed TypeScript route builders for the client
- typed Kotlin route objects and application configs for the server
- bundle entry metadata for webpack or Vite

## Modules

- `spa-routing-core`: route, application, and access contracts plus generators
- `spa-routing-gradle-plugin`: Gradle integration for code generation
- `spa-routing-runtime`: framework-neutral registration, validation, access evaluation, destination resolution, and HTML generation
- `spa-routing-ktor`: Ktor route registration, request conversion, and HTTP responses
- `spa-routing-spring-boot-autoconfigure` / `-starter`: Spring MVC adapters, properties, and bean wiring

The runtime depends on core and has no framework dependency. See the
[runtime guide](docs/runtime.md) and [Ktor setup](docs/ktor.md).

## Runnable Examples

The [examples](examples/README.md) run the same blog on Spring Boot or Ktor,
sharing service logic, access handlers, generated routes, and a React frontend:

```sh
./gradlew :examples:spring:run
# Or run Ktor:
./gradlew :examples:ktor:run
```

Open [http://localhost:8080/](http://localhost:8080/) for Spring or
[http://localhost:8082/](http://localhost:8082/) for Ktor.

## Install

These examples target the unreleased `0.5.0-SNAPSHOT` API. Build it with
`./gradlew publishToMavenLocal` and add `mavenLocal()` to your dependency and
plugin repositories. The latest published release is `0.4.0`.

For Gradle route generation:

```kotlin
plugins {
  id("io.github.caseymcguire.spa-routing") version "0.5.0-SNAPSHOT"
}

dependencies {
  implementation("io.github.caseymcguire:spa-routing-core:0.5.0-SNAPSHOT")
}
```

Generated server sources and authoring definitions need only `spa-routing-core` to compile. Add `spa-routing-runtime` for serving routes, or use the Spring starter below.

The `:spa-route-definitions` project contains your authored `SinglePageApplicationDefinition` objects. Configure it through `routeDefinitions.projectPath`; the plugin uses its compiled classes on the generator classpath. The server consumes generated configs and does not need an implementation dependency on the definitions project.

For Spring Boot route serving:

```kotlin
dependencies {
  implementation("io.github.caseymcguire:spa-routing-spring-boot-starter:0.5.0-SNAPSHOT")
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
    route("users/{id}", "UserDetail"),
    route("not-found", "NotFound"),
    route("error", "Error")
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
gate is public. Application access is configured separately on the server;
apps that need that check on each navigation must still call the endpoint.

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

## Generated Server Configuration

`generateServerRoutes` emits a concrete `<ApplicationName>ApplicationConfig`,
a typed `<ApplicationName>ApplicationAccessHandler` base class, and a required
`<ApplicationName>RouteAccessHandlers` collection next to the route builders.
For the blog, construction is ordinary Kotlin:

```kotlin
import com.sparouting.runtime.rendering.HtmlDocumentRenderer

val config = BlogApplicationConfig(
  applicationAccessHandler = checkBlogAccess,
  routeAccessHandlers = BlogRouteAccessHandlers(
    post = checkPostAccess,
    editPost = checkEditPostAccess
  ),
  htmlRenderer = HtmlDocumentRenderer()
)
```

The config implements `SinglePageApplicationConfig` and contains generated
`id`, `name`, `bundleName`, and `routes` properties. Each `RouteManifest` includes
its full path pattern, path/query metadata, and `hasAccessHandler` flag.
The handler collection requires the generated handler type for every gated route;
missing or mismatched arguments fail compilation. An application with no gated
routes has a zero-argument collection.

Generated server configs depend only on `spa-routing-core` and have no framework
annotations. Authoring definitions and build-only fields such as `appRootPath`
stay in the generation step; the server needs no definitions-project dependency.

## Spring Boot Runtime

The starter discovers `SinglePageApplicationConfig` beans and reads the handlers
from each config. For complete setup, see
[docs/spring-boot-client-apps.md](docs/spring-boot-client-apps.md).
A public application with no gated routes can be wired as follows:

```kotlin
import com.example.generated.spa.routes.AccountApplicationConfig
import com.example.generated.spa.routes.AccountApplicationAccessHandler
import com.example.generated.spa.routes.AccountRouteAccessHandlers
import com.example.generated.spa.routes.AccountRoutes
import com.sparouting.runtime.evaluation.DefaultRouteFailureHandler
import com.sparouting.runtime.evaluation.RouteFailureHandler
import com.sparouting.contract.AccessDecision
import com.sparouting.contract.RouteRequest
import com.sparouting.runtime.rendering.HtmlDocumentRenderer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component

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

@Component
class CheckAccountAccess : AccountApplicationAccessHandler() {
  override fun evaluate(request: RouteRequest): AccessDecision = AccessDecision.Allowed
}
```

The application handler must return `AccessDecision.Allowed` before the matching
route handler runs. Either handler can return `AccessDecision.Denied(reason, destination)`.
Unflagged routes are allowed after the application check succeeds.
`AccountApplicationAccessHandler` extends
`ApplicationAccessHandler<AccountApplicationConfig>` and takes no config instance.
Inject authentication or permission services into the concrete handler as needed.

For route checks, declare `generateAccessHandler = true`, extend the generated
`<Route>AccessHandler`, and supply the implementation to the generated handler
collection. The starter reads only the handlers attached to configs; standalone
handler beans are not automatically registered for access evaluation.
Both page loads and navigation checks use the same `RouteRequest` and checks.
See [generated access handlers](docs/spring-boot-client-apps.md#generated-access-handlers)
and the [blog example](examples/README.md).

Both levels share the same decision type. For example:

```kotlin
import com.example.generated.spa.routes.AccountRoutes
import com.sparouting.contract.AccessDecision
import com.sparouting.contract.DenialReason

AccessDecision.Denied(
  reason = DenialReason(code = "profile_required", message = "Complete your profile to continue."),
  destination = AccountRoutes.UserDetail(id = "123")
)
```

Each config supplies a framework-neutral `HtmlRenderer`. Use `HtmlDocumentRenderer`
for the default shell or supply your own implementation returning an HTML string.
See [HTML rendering](docs/spring-boot-client-apps.md#render-html) for configuration.

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
type RouteDecision =
  | { type: "allowed" }
  | {
      type: "denied" | "unknown_route" | "invalid_request";
      destination: string;
      reason: { code: string; message: string };
    };
const decision = await response.json() as RouteDecision;
```

Useful Spring properties:

```yaml
spa-routing:
  server:
    enabled: true
  route-decision:
    enabled: true
    path: /__spa/route-decision
```

Override the `RouteRequestFactory` bean to customize page request conversion.
Supply a `RouteFailureHandler` to choose recovery destinations for unknown routes
and invalid requests. Its choices apply to both page loads and client navigation.
Override `RouteHttpResponseConverter` for page HTTP mapping only; Ktor accepts it
as `responseConverter`. Navigation JSON contains a semantic `type` and a required
`destination` and `reason` on every failure.
See [custom HTTP conversion](docs/runtime.md#customize-http-conversion).
Configure rendering and asset options through each config's `htmlRenderer`.

The adapters construct a private `RouteRequestEvaluator` from application configs
and serialize its `RouteResult` for navigation checks. Page loads pass the result
through the HTTP converter. Configure access through application and route handlers;
registration stays internal.

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
