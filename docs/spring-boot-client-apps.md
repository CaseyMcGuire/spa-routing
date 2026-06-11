# Spring Boot Client Apps

This guide is for Spring Boot applications that want to serve SPA routes using
`spa-routing`.

Use the starter when your app already has `SpaApplicationDefinition` objects and
you want Spring to handle:

- registering MVC `GET` routes for each SPA route
- validating path parameters
- evaluating app and route rules
- resolving raw and typed redirects
- rendering a default SPA HTML page
- exposing `SpaRouteResponseService` for app-owned GraphQL or REST route checks

Your application still owns:

- route definitions
- `SinglePageApplicationConfig` beans
- app-specific authorization or redirect rules
- custom HTML rendering, if the default page is not enough
- any public GraphQL or REST endpoint that calls `SpaRouteResponseService`

## Add Dependencies

Add the Spring Boot starter to the Spring application that will serve the SPA
routes:

```kotlin
dependencies {
  implementation("io.github.caseymcguire:spa-routing-spring-boot-starter:0.1.3")
  implementation(project(":spa-route-definitions"))
}
```

The `:spa-route-definitions` dependency is the project where your concrete
`SpaApplicationDefinition` objects live.

The starter targets Spring Boot 4.x and brings in `spring-boot-starter-web`.
Use it from Spring Boot 4 applications.

Prefer the starter for normal applications. If you depend directly on
`spa-routing-spring-boot-autoconfigure`, your app still needs Spring Boot and
Spring MVC on its classpath because the public runtime API exposes Spring MVC
types such as `ServerResponse`.

## Public Packages

Client apps usually import from these packages:

- `io.github.caseymcguire.sparouting.spring.config`: SPA config beans and route registry
- `io.github.caseymcguire.sparouting.spring.rules`: rule interfaces, results, actions, resolver, and evaluator
- `io.github.caseymcguire.sparouting.spring.request`: request model and request factory
- `io.github.caseymcguire.sparouting.spring.response`: route-decision request, response, and service
- `io.github.caseymcguire.sparouting.spring.rendering`: HTML renderer interfaces and defaults
- `io.github.caseymcguire.sparouting.spring.web`: Spring MVC router factory
- `io.github.caseymcguire.sparouting.spring.autoconfigure`: Spring Boot properties and auto-configuration

If the same Spring app also generates server route objects for route-level rules
or typed redirects, apply and configure the Gradle plugin too:

```kotlin
plugins {
  id("io.github.caseymcguire.spa-routing") version "0.1.3"
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

  webpackBundleEntries {
    outputFile = "SinglePageApplicationBundles.ts"
  }
}
```

## Define Config Beans

For each SPA that should be served, expose a `SinglePageApplicationConfig` bean.
The starter reads these beans during auto-configuration.

```kotlin
package com.example.web

import com.example.routes.AccountSpaApplication
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

If `AccountSpaApplication.urlPrefix` is `account` and it defines
`route("users/{id}", "UserDetail")`, the starter registers:

```text
GET /account/users/{id}
```

The route only matches `GET`. Invalid path parameter values return
`spa-routing.server.invalid-path-parameter-status`, which defaults to `400`.

## Add Rules

Application-wide rules run for every route in that SPA:

```kotlin
import io.github.caseymcguire.sparouting.spring.config.SinglePageApplicationConfig
import io.github.caseymcguire.sparouting.spring.request.SpaRouteRequest
import io.github.caseymcguire.sparouting.spring.rules.SpaRouteRule
import io.github.caseymcguire.sparouting.spring.rules.SpaRouteRuleAction
import io.github.caseymcguire.sparouting.spring.rules.SpaRouteRuleResult
import org.springframework.context.annotation.Bean

class RequireLogin : SpaRouteRule {
  override fun evaluate(request: SpaRouteRequest): SpaRouteRuleResult {
    return if (request.header("X-User").isEmpty()) {
      SpaRouteRuleResult.Deny(SpaRouteRuleAction.redirect("/login"))
    } else {
      SpaRouteRuleResult.Skip
    }
  }
}

@Bean
fun accountSpaConfig(): SinglePageApplicationConfig {
  return object : SinglePageApplicationConfig {
    override val application = AccountSpaApplication
    override val rules = listOf(RequireLogin())
  }
}
```

Rule results behave like this:

- `Skip`: continue to the next rule
- `Allow`: stop evaluating and serve the SPA HTML
- `Deny`: stop evaluating and return the configured status or redirect

Use `Skip` for a rule that passes but should still allow later route-level
rules to run. Use `Allow` only when the rule should explicitly bypass the rest
of the chain.

Route-level rules use generated server route objects as keys:

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

For a request to `UserDetail`, the starter evaluates:

```kotlin
config.rules + config.getRouteRules(route)
```

## Redirect From Rules

Use a raw location when the target is outside the SPA route definitions:

```kotlin
SpaRouteRuleResult.Deny(SpaRouteRuleAction.redirect("/login"))
```

Use a typed generated route target when redirecting to another SPA route:

```kotlin
import com.example.generated.spa.routes.AccountRoutes

SpaRouteRuleResult.Deny(
  SpaRouteRuleAction.redirectTo(AccountRoutes.UserDetail(id = 123))
)
```

Typed redirects are validated against the target route parameters. Unknown
applications, unknown routes, and invalid target parameters fail with clear
startup or runtime errors instead of producing broken URLs.

## Render HTML

By default, the starter renders a small HTML page:

```html
<div id="root"></div>
<script type="module" src="/bundles/{bundleName}.bundle.js"></script>
```

It can also include route CSS and a global stylesheet through properties.

Override rendering for one SPA by implementing `renderHtml()`:

```kotlin
import io.github.caseymcguire.sparouting.spring.config.SinglePageApplicationConfig
import org.springframework.http.MediaType
import org.springframework.context.annotation.Bean
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

Override rendering for all SPAs by replacing the `SpaHtmlRenderer` bean:

```kotlin
import io.github.caseymcguire.sparouting.spring.config.SinglePageApplicationConfig
import io.github.caseymcguire.sparouting.spring.rendering.SpaHtmlRenderer
import org.springframework.context.annotation.Bean
import org.springframework.web.servlet.function.ServerResponse

@Bean
fun spaHtmlRenderer(): SpaHtmlRenderer {
  return object : SpaHtmlRenderer {
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
  assets:
    bundle-base-path: /bundles
    include-route-stylesheet: true
    global-stylesheet: /bundles/stylex.css
```

Set `spa-routing.server.enabled=false` when the application only wants the
route-decision service and does not want the starter to register MVC SPA routes.

## Use Route Decisions From GraphQL Or REST

The starter provides `SpaRouteResponseService` for client-side navigation checks.
It is intentionally not exposed as a built-in HTTP endpoint; the Spring app owns
its GraphQL or REST API shape.

```kotlin
import io.github.caseymcguire.sparouting.spring.response.SpaRouteResponseRequest
import io.github.caseymcguire.sparouting.spring.response.SpaRouteResponseService

class SpaRouteDecisionHandler(
  private val spaRouteResponseService: SpaRouteResponseService
) {
  fun evaluateAccountRoute(
    routeId: String,
    parameters: Map<String, String>,
    headers: Map<String, List<String>>
  ) = spaRouteResponseService.evaluate(
    SpaRouteResponseRequest(
      applicationId = "account",
      routeId = routeId,
      parameters = parameters,
      headers = headers
    )
  )
}
```

The response shape is:

```kotlin
data class SpaRouteHttpResponse(
  val statusCode: Int,
  val location: String? = null
)
```

This lets a GraphQL resolver or REST controller return the same decision the MVC
route would use:

- `200`: navigation is allowed
- `302` with `location`: redirect
- `400`: invalid route parameters
- `404`: unknown route
- any other 3xx, 4xx, or 5xx returned by your rules

## Replace Starter Beans

Define your own bean when the defaults are not enough:

```kotlin
import io.github.caseymcguire.sparouting.spring.request.SpaRouteRequestFactory
import org.springframework.context.annotation.Bean

@Bean
fun spaRouteRequestFactory(): SpaRouteRequestFactory = MySpaRouteRequestFactory()
```

Replaceable beans:

- `SinglePageApplicationRouteRegistry`
- `SpaRouteRuleActionResolver`
- `SpaRouteResponseEvaluator`
- `SpaRouteRequestFactory`
- `SpaHtmlRenderer`
- `SpaRouteResponseService`

## Migration Checklist

For an existing Spring app that copied SPA routing code locally:

1. Add `spa-routing-spring-boot-starter`.
2. Keep app-owned `SpaApplicationDefinition` objects in the route definitions project.
3. Keep app-owned rules, but switch imports to the `io.github.caseymcguire.sparouting.spring.*` subpackages.
4. Replace copied registry, evaluator, resolver, request adapter, and response classes with the starter.
5. Expose one `SinglePageApplicationConfig` bean per SPA.
6. Move any app-specific HTML page rendering into `renderHtml()` or a `SpaHtmlRenderer` bean.
7. Use `SpaRouteResponseService` from your existing GraphQL or REST route-decision endpoint.
