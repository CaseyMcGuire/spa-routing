# spa-routing

`spa-routing` lets a Kotlin server and a TypeScript SPA share one route definition source. You define your SPA routes once in Kotlin, then generate:

- typed TypeScript route builders for the client
- typed Kotlin route objects for the server
- webpack bundle entry metadata

## Install

```kotlin
plugins {
  id("io.github.caseymcguire.spa-routing") version "0.1.2"
}

dependencies {
  implementation("io.github.caseymcguire:spa-routing-core:0.1.2")
  implementation(project(":spa-route-definitions"))
}
```

The `:spa-route-definitions` project is your app-owned module containing concrete `SpaApplicationDefinition` objects. The plugin needs that project on the generator classpath because route discovery loads those objects at runtime.

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
