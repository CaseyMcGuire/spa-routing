import org.gradle.api.attributes.Usage

plugins {
  java
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(21))
  }
}

val routeCodegen by configurations.creating {
  isCanBeConsumed = false
  attributes {
    attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
  }
}

dependencies {
  routeCodegen(project(":examples:route-definitions"))
}

val routeDefinitionsDirectory = project(":examples:route-definitions")
  .layout.projectDirectory.dir("src/main/kotlin/com/sparouting/contract/applications")
val clientRoutesDirectory = layout.buildDirectory.dir("generated/client/routes")
val frontendResourcesDirectory = layout.buildDirectory.dir("generated/frontend")

val generateClientRoutes by tasks.registering(JavaExec::class) {
  group = "spa routing"
  description = "Generates TypeScript routes for the shared blog frontend."
  classpath = routeCodegen
  mainClass.set("com.sparouting.contract.codegen.GenerateClientRoutesKt")
  inputs.dir(routeDefinitionsDirectory)
  outputs.dir(clientRoutesDirectory)
  systemProperty("spa.application.source.dir", routeDefinitionsDirectory.asFile.absolutePath)
  systemProperty("route.output.dir", clientRoutesDirectory.get().asFile.absolutePath)
}

val installFrontendDependencies by tasks.registering(Exec::class) {
  group = "build"
  description = "Installs the blog frontend's locked dependencies."
  workingDir(layout.projectDirectory)
  commandLine("npm", "ci", "--no-audit", "--no-fund")
  inputs.files("package.json", "package-lock.json")
  outputs.dir("node_modules")
}

val buildFrontend by tasks.registering(Exec::class) {
  group = "build"
  description = "Builds the shared blog frontend assets."
  dependsOn(installFrontendDependencies, generateClientRoutes)
  workingDir(layout.projectDirectory)
  commandLine("npm", "run", "build")
  inputs.dir("src")
  inputs.dir(clientRoutesDirectory)
  inputs.files("package.json", "package-lock.json", "tsconfig.json", "vite.config.mjs")
  outputs.dir(frontendResourcesDirectory)
}

// Servers consume this resource-only JAR through a runtime dependency.
sourceSets.main {
  resources.srcDir(buildFrontend)
}
