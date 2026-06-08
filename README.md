# spa-routing

Reusable SPA routing contract, code generators, and Gradle plugin.

## Core Artifact

`com.caseymcguire:spa-routing-core:0.1.0-SNAPSHOT`

## Gradle Plugin

`com.caseymcguire.spa-routing`

## Tasks

The plugin adds these tasks to a consuming app:

- `generateClientRoutes`
- `generateServerSpaRoutes`
- `generateWebpackBundleEntries`

Each task fails with an explicit error if its required `spaRouting` configuration is missing.

## Consumer Setup

Consumers define concrete `SpaApplicationDefinition` objects in their own project. The generator tasks need that route definitions project's compiled `main` runtime classpath because discovery loads those objects with `Class.forName`.

```kotlin
plugins {
  id("com.caseymcguire.spa-routing") version "0.1.0-SNAPSHOT"
}

dependencies {
  implementation("com.caseymcguire:spa-routing-core:0.1.0-SNAPSHOT")
  implementation(project(":spa-route-definitions"))
}

spaRouting {
  configuration {
    routeDefinitions {
      projectPath = ":spa-route-definitions"
    }

    clientRoutes {
      target {
        directory = "src/main/web-frontend/__generated__/routes"
      }
    }

    serverRoutes {
      target {
        packageName = "com.caseymcguiredotcom.generated.spa.routes"
        directory = "build/generated/source/spaRoutes/main"
      }
    }

    webpackBundleEntries {
      target {
        file = "SinglePageApplicationBundles.ts"
      }
    }
  }
}
```

If `org.jetbrains.kotlin.jvm` is applied in the consuming app, the plugin adds `serverRoutesSourceRoot` to the main Kotlin source set and makes Kotlin compilation depend on `generateServerSpaRoutes`.

Do not point route generation only at the external `spa-routing-core` jar. Keep the consumer route definitions project on the generator runtime classpath through `routeDefinitionsProject`.

Required configuration:

- `routeDefinitionsProject`
- `applicationSourceDir`
- `clientRoutesOutputDir`, for `generateClientRoutes`
- `webpackBundleEntriesOutputFile`, for `generateWebpackBundleEntries`

Defaults:

- `applicationSourceDir`: `src/main/kotlin/com/caseymcguiredotcom/sparoutecontract/applications` in the configured route definitions project
- `serverRoutesPackage`: `com.caseymcguiredotcom.generated.spa.routes`
- `serverRoutesSourceRoot`: `build/generated/source/spaRoutes/main`
- `serverRoutesOutputDir`: derived from `serverRoutesSourceRoot` and `serverRoutesPackage`

## Generator Main Classes

- `com.caseymcguiredotcom.sparoutecontract.codegen.GenerateClientRoutesKt`
- `com.caseymcguiredotcom.sparoutecontract.codegen.GenerateServerRoutesKt`
- `com.caseymcguiredotcom.sparoutecontract.codegen.GenerateWebpackBundleEntriesKt`

## Validation

```sh
./gradlew clean build publishToMavenLocal
```

This publishes the core artifact and the Gradle plugin marker to Maven local.
