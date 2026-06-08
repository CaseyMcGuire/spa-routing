# spa-routing

Reusable SPA routing contract and code generators.

## Artifact

`com.caseymcguire:spa-routing:0.1.0-SNAPSHOT`

## Consumer Setup

Consumers define their application-specific `SpaApplicationDefinition` objects in their own source set or module. Those route definitions must be compiled and present on the generator runtime classpath.

Generator tasks must point `spa.application.source.dir` at the consumer route definition source directory and `route.output.dir` at the directory where generated route files should be written.

## Generator Main Classes

- `com.caseymcguiredotcom.sparoutecontract.codegen.GenerateClientRoutesKt`
- `com.caseymcguiredotcom.sparoutecontract.codegen.GenerateServerRoutesKt`
- `com.caseymcguiredotcom.sparoutecontract.codegen.GenerateWebpackBundleEntriesKt`

## Required Generator Properties

- `spa.application.source.dir`
- `route.output.dir`
- `route.server.package`, optional for server route generation

See the consumer Gradle wiring below for a complete task setup.

## Consumer Gradle Wiring

The consuming application owns concrete `SpaApplicationDefinition` objects. Generator tasks must run with those compiled route definitions on the runtime classpath so `SpaApplicationDefinitionDiscovery` can load them with `Class.forName`.

```kotlin
import org.gradle.api.tasks.SourceSetContainer
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

dependencies {
  implementation("com.caseymcguire:spa-routing:0.1.0-SNAPSHOT")
  implementation(project(":spa-route-definitions"))
}

kotlin {
  sourceSets {
    main {
      kotlin.srcDir(layout.buildDirectory.dir("generated/source/spaRoutes/main"))
    }
  }
}

val clientRoutePath = "com.caseymcguiredotcom.sparoutecontract.codegen.GenerateClientRoutesKt"
val serverRoutePath = "com.caseymcguiredotcom.sparoutecontract.codegen.GenerateServerRoutesKt"
val webpackEntryPath = "com.caseymcguiredotcom.sparoutecontract.codegen.GenerateWebpackBundleEntriesKt"

val spaApplicationSourceDir = project(":spa-route-definitions")
  .layout
  .projectDirectory
  .dir("src/main/kotlin/com/caseymcguiredotcom/sparoutecontract/applications")
  .asFile
  .absolutePath

fun spaRouteDefinitionsRuntimeClasspath() = project(":spa-route-definitions")
  .extensions
  .getByType<SourceSetContainer>()
  .named("main")
  .get()
  .runtimeClasspath

tasks.register<JavaExec>("generateClientRoutes") {
  description = "Generates typed TypeScript SPA routes for the client."
  dependsOn(":spa-route-definitions:classes")
  classpath = spaRouteDefinitionsRuntimeClasspath()
  mainClass.set(clientRoutePath)
  systemProperty("spa.application.source.dir", spaApplicationSourceDir)
  systemProperty("route.output.dir", "src/main/web-frontend/__generated__/routes")
}

val generatedServerSpaRoutesDir = layout.buildDirectory
  .dir("generated/source/spaRoutes/main/com/caseymcguiredotcom/generated/spa/routes")

tasks.register<JavaExec>("generateServerSpaRoutes") {
  description = "Generates typed Kotlin SPA route objects for the server."
  dependsOn(":spa-route-definitions:classes")
  classpath = spaRouteDefinitionsRuntimeClasspath()
  mainClass.set(serverRoutePath)
  systemProperty("spa.application.source.dir", spaApplicationSourceDir)
  systemProperty("route.output.dir", generatedServerSpaRoutesDir.get().asFile.absolutePath)
  systemProperty("route.server.package", "com.caseymcguiredotcom.generated.spa.routes")
}

tasks.withType<KotlinCompile>().configureEach {
  dependsOn("generateServerSpaRoutes")
}

tasks.register<JavaExec>("generateWebpackBundleEntries") {
  description = "Generates a file containing the path to each React app's entry point."
  dependsOn(":spa-route-definitions:classes")
  classpath = spaRouteDefinitionsRuntimeClasspath()
  mainClass.set(webpackEntryPath)
  systemProperty("spa.application.source.dir", spaApplicationSourceDir)
  systemProperty("route.output.dir", "SinglePageApplicationBundles.ts")
}
```

Do not point these generator tasks only at the external `spa-routing` jar. They need both the generator classes and the compiled consumer route definition objects.
