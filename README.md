# spa-routing

`spa-routing` lets a Kotlin server and a TypeScript SPA share one route definition source. You define your SPA routes once in Kotlin, then generate:

- typed TypeScript route builders for the client
- typed Kotlin route objects for the server
- webpack bundle entry metadata

## Install

For Gradle route generation:

```kotlin
plugins {
  id("io.github.caseymcguire.spa-routing") version "0.1.4"
}

dependencies {
  implementation("io.github.caseymcguire:spa-routing-core:0.1.4")
  implementation(project(":spa-route-definitions"))
}
```

The `:spa-route-definitions` project is your app-owned module containing concrete `SpaApplicationDefinition` objects. The plugin needs that project on the generator classpath because route discovery loads those objects at runtime.

For Spring Boot route serving:

```kotlin
dependencies {
  implementation("io.github.caseymcguire:spa-routing-spring-boot-starter:0.1.4")
  implementation(project(":spa-route-definitions"))
}
```

The starter targets Spring Boot 4.x and brings in `spring-boot-starter-web`.
Use it from Spring Boot 4 applications.

## Define Routes

Create route definitions in a dedicated module, commonly under:

```txt
spa-route-definitions/src/main/kotlin/com/caseymcguiredotcom/sparoutecontract/applications
```

Example:

```kotlin
package com.caseymcguiredotcom.sparoutecontract.applications

import com.caseymcguiredotcom.sparoutecontract.SpaApplicationDefinition
import com.caseymcguiredotcom.sparoutecontract.int
import com.caseymcguiredotcom.sparoutecontract.route

object AccountSpaApplication : SpaApplicationDefinition {
  override val id = "account"
  override val name = "Account"
  override val urlPrefix = "account"
  override val appRootPath = "src/main/web-frontend/apps/account"
  override val routes = listOf(
    route("settings", "Settings"),
    route("users/{id}", "UserDetail", parameters = listOf(int("id")))
  )
}
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

  webpackBundleEntries {
    outputFile = "SinglePageApplicationBundles.ts"
  }
}
```

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
- `generateServerSpaRoutes`
- `generateWebpackBundleEntries`

Run all three manually:

```sh
./gradlew generateClientRoutes generateServerSpaRoutes generateWebpackBundleEntries
```

When `org.jetbrains.kotlin.jvm` is applied, `generateServerSpaRoutes` is wired into Kotlin compilation and `serverRoutes.sourceRoot` is added as a generated source root.

## Defaults

- `routeDefinitions.sourceDirectory`: `src/main/kotlin/com/caseymcguiredotcom/sparoutecontract/applications`
- `serverRoutes.packageName`: `com.caseymcguiredotcom.generated.spa.routes`
- `serverRoutes.sourceRoot`: `build/generated/source/spaRoutes/main`
- server route output directory: derived from `serverRoutes.sourceRoot` and the configured package name

The client routes output directory and webpack bundle entries output file are required because they are application-specific.

## Troubleshooting

If a generator task fails with `spaRouting.<name> must be set`, the plugin is missing required configuration for that task.

If discovery fails to find or load route definitions, check that:

- `routeDefinitions.projectPath` points to the module with your concrete `SpaApplicationDefinition` objects
- that module applies the Java or Kotlin JVM plugin
- `routeDefinitions.sourceDirectory` points at the Kotlin source directory containing those objects

Do not point generation only at `spa-routing-core`; the generators need the compiled app-specific route definition classes too.

## Spring Boot Runtime

The Spring Boot starter serves configured SPA routes from app-provided `SinglePageApplicationConfig` beans. Application code owns the configs and rules; the starter owns the registry, route matching, rule evaluation, redirects, the route decision endpoint, and default HTML response.

For complete client setup, route rules, HTML rendering, properties, and route decision examples, see [docs/spring-boot-client-apps.md](docs/spring-boot-client-apps.md).

```kotlin
import io.github.caseymcguire.sparouting.spring.config.SinglePageApplicationConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SpaRoutesConfiguration {
  @Bean
  fun accountSpaConfig(): SinglePageApplicationConfig {
    return object : SinglePageApplicationConfig {
      override val application = AccountSpaApplication
    }
  }
}
```

Add application-wide rules when every route in an SPA needs the same behavior:

```kotlin
import io.github.caseymcguire.sparouting.spring.request.SpaRouteRequest
import io.github.caseymcguire.sparouting.spring.rules.SpaRouteRule
import io.github.caseymcguire.sparouting.spring.rules.SpaRouteRuleAction
import io.github.caseymcguire.sparouting.spring.rules.SpaRouteRuleResult

class RequireLogin : SpaRouteRule {
  override fun evaluate(request: SpaRouteRequest): SpaRouteRuleResult {
    return if (request.header("X-User").isEmpty()) {
      SpaRouteRuleResult.Deny(SpaRouteRuleAction.redirect("/login"))
    } else {
      SpaRouteRuleResult.Skip
    }
  }
}
```

Attach app-wide rules and route-level rules from a config bean:

```kotlin
import com.example.generated.spa.routes.AccountRoutes
import io.github.caseymcguire.sparouting.spring.config.SinglePageApplicationConfig
import org.springframework.context.annotation.Bean

@Bean
fun accountSpaConfig(): SinglePageApplicationConfig {
  return object : SinglePageApplicationConfig {
    override val application = AccountSpaApplication
    override val rules = listOf(RequireLogin())
    override val routeRules = mapOf(
      AccountRoutes.UserDetail to listOf(RequireAccountAccess())
    )
  }
}
```

Redirect to a raw URL or a generated typed SPA route:

```kotlin
import com.example.generated.spa.routes.AccountRoutes
import io.github.caseymcguire.sparouting.spring.rules.SpaRouteRuleAction
import io.github.caseymcguire.sparouting.spring.rules.SpaRouteRuleResult

SpaRouteRuleResult.Deny(SpaRouteRuleAction.redirect("/login"))

SpaRouteRuleResult.Deny(
  SpaRouteRuleAction.redirectTo(AccountRoutes.UserDetail(id = 123))
)
```

Override the default HTML page for one SPA:

```kotlin
import io.github.caseymcguire.sparouting.spring.config.SinglePageApplicationConfig
import org.springframework.context.annotation.Bean
import org.springframework.http.MediaType
import org.springframework.web.servlet.function.ServerResponse

@Bean
fun accountSpaConfig(): SinglePageApplicationConfig {
  return object : SinglePageApplicationConfig {
    override val application = AccountSpaApplication

    override fun renderHtml(): ServerResponse? {
      return ServerResponse.ok()
        .contentType(MediaType.TEXT_HTML)
        .body(AccountPage().render())
    }
  }
}
```

Check a client-side navigation before changing routes:

```ts
const params = new URLSearchParams({
  applicationId: "account",
  routeId: "UserDetail",
  "parameters.id": "123",
  "queryParameters.tab": "billing",
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
  route-decision:
    enabled: true
    path: /__spa/route-decision
  assets:
    bundle-base-path: /bundles
    include-route-stylesheet: true
    global-stylesheet: /bundles/stylex.css
```

Override these beans to customize runtime behavior:

- `SpaHtmlRenderer`
- `SpaRouteRuleActionResolver`
- `SpaRouteResponseEvaluator`
- `SpaRouteRequestFactory`
- `SinglePageApplicationRouteRegistry`
- `SpaRouteResponseService`

## Development

Build and test:

```sh
./gradlew clean build
```

Publish locally:

```sh
./gradlew publishToMavenLocal
```

Release publishing instructions are in [docs/publishing.md](docs/publishing.md).
