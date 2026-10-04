import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("org.jetbrains.kotlin.jvm")
  application
}

val springBootVersion: String by rootProject.extra

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(21))
  }
}

kotlin {
  jvmToolchain(21)
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_21)
  }
}

application {
  mainClass.set("com.sparouting.examples.spring.SpringExampleApplicationKt")
}

dependencies {
  implementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
  implementation(project(":examples:route-definitions"))
  implementation(project(":spa-routing-spring-boot-starter"))
  implementation("tools.jackson.module:jackson-module-kotlin")
}

val routeDefinitionsDirectory = project(":examples:route-definitions")
  .layout.projectDirectory.dir("src/main/kotlin/com/sparouting/contract/applications")
val serverRoutesDirectory = layout.buildDirectory.dir("generated/source/spaRoutes/main")
val clientRoutesDirectory = layout.buildDirectory.dir("generated/client/routes")

// Use this checkout's generator entry points: its Gradle plugin is a sibling
// project and cannot be resolved through this build's plugins block.
val generateServerSpaRoutes by tasks.registering(JavaExec::class) {
  group = "spa routing"
  description = "Generates Kotlin routes from the shared blog definitions."
  classpath = configurations.runtimeClasspath.get()
  mainClass.set("com.sparouting.contract.codegen.GenerateServerRoutesKt")
  inputs.dir(routeDefinitionsDirectory)
  outputs.dir(serverRoutesDirectory)
  systemProperty("spa.application.source.dir", routeDefinitionsDirectory.asFile.absolutePath)
  systemProperty("route.output.dir", serverRoutesDirectory.get().asFile.absolutePath)
  systemProperty("route.server.package", "com.sparouting.examples.generated.routes")
}

val generateClientRoutes by tasks.registering(JavaExec::class) {
  group = "spa routing"
  description = "Generates TypeScript routes from the shared blog definitions."
  classpath = configurations.runtimeClasspath.get()
  mainClass.set("com.sparouting.contract.codegen.GenerateClientRoutesKt")
  inputs.dir(routeDefinitionsDirectory)
  outputs.dir(clientRoutesDirectory)
  systemProperty("spa.application.source.dir", routeDefinitionsDirectory.asFile.absolutePath)
  systemProperty("route.output.dir", clientRoutesDirectory.get().asFile.absolutePath)
}

kotlin.sourceSets.named("main") {
  kotlin.srcDir(serverRoutesDirectory)
}

tasks.withType<KotlinCompile>().configureEach {
  dependsOn(generateServerSpaRoutes)
}
