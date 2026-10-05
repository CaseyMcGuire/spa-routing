import org.gradle.api.attributes.Usage
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("org.jetbrains.kotlin.jvm")
  `java-library`
}

kotlin {
  jvmToolchain(21)
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_21)
  }
}

val routeCodegen by configurations.creating {
  isCanBeConsumed = false
  attributes {
    attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
  }
}

dependencies {
  api(project(":spa-routing-core"))
  routeCodegen(project(":examples:route-definitions"))
}

val routeDefinitionsDirectory = project(":examples:route-definitions")
  .layout.projectDirectory.dir("src/main/kotlin/com/sparouting/contract/applications")
val serverRoutesDirectory = layout.buildDirectory.dir("generated/source/spaRoutes/main")

// Use the sibling core project's generator without resolving a published plugin.
val generateServerRoutes by tasks.registering(JavaExec::class) {
  group = "spa routing"
  description = "Generates Kotlin application configuration and routes for the shared blog module."
  classpath = routeCodegen
  mainClass.set("com.sparouting.contract.codegen.GenerateServerRoutesKt")
  inputs.dir(routeDefinitionsDirectory)
  outputs.dir(serverRoutesDirectory)
  systemProperty("spa.application.source.dir", routeDefinitionsDirectory.asFile.absolutePath)
  systemProperty("route.output.dir", serverRoutesDirectory.get().asFile.absolutePath)
  systemProperty("route.server.package", "com.sparouting.examples.generated.routes")
}

kotlin.sourceSets.named("main") {
  kotlin.srcDir(serverRoutesDirectory)
}

tasks.withType<KotlinCompile>().configureEach {
  dependsOn(generateServerRoutes)
}
