package com.caseymcguire.sparouting.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.GradleException
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

class SpaRoutingPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val extension = project.extensions.create("spaRouting", SpaRoutingExtension::class.java)

    extension.serverRoutesPackage.convention(DEFAULT_SERVER_ROUTES_PACKAGE)
    extension.serverRoutesSourceRoot.convention(
      project.layout.buildDirectory.dir("generated/source/spaRoutes/main")
    )
    extension.serverRoutesOutputDir.convention(
      extension.serverRoutesSourceRoot.zip(extension.serverRoutesPackage) { sourceRoot, packageName ->
        sourceRoot.dir(packageName.replace(".", "/"))
      }
    )

    val routeDefinitionClasspath = project.objects.fileCollection().from(
      project.provider { extension.requiredRouteDefinitionsProject().mainRuntimeClasspath() }
    )
    val routeDefinitionClasses = project.provider {
      "${extension.requiredRouteDefinitionsProject().path}:classes"
    }

    project.tasks.register("generateClientRoutes", JavaExec::class.java) { task ->
      task.description = "Generates typed TypeScript SPA routes for the client."
      task.group = TASK_GROUP
      task.dependsOn(routeDefinitionClasses)
      task.classpath(routeDefinitionClasspath)
      task.mainClass.set(CLIENT_ROUTE_MAIN_CLASS)
      task.doFirst {
        extension.requiredRouteDefinitionsProject()
        task.systemProperty(
          "spa.application.source.dir",
          extension.applicationSourceDir.required("applicationSourceDir").asFile.absolutePath
        )
        task.systemProperty(
          "route.output.dir",
          extension.clientRoutesOutputDir.required("clientRoutesOutputDir").asFile.absolutePath
        )
      }
    }

    val generateServerSpaRoutes = project.tasks.register("generateServerSpaRoutes", JavaExec::class.java) { task ->
      task.description = "Generates typed Kotlin SPA route objects for the server."
      task.group = TASK_GROUP
      task.dependsOn(routeDefinitionClasses)
      task.classpath(routeDefinitionClasspath)
      task.mainClass.set(SERVER_ROUTE_MAIN_CLASS)
      task.doFirst {
        extension.requiredRouteDefinitionsProject()
        task.systemProperty(
          "spa.application.source.dir",
          extension.applicationSourceDir.required("applicationSourceDir").asFile.absolutePath
        )
        task.systemProperty(
          "route.output.dir",
          extension.serverRoutesOutputDir.required("serverRoutesOutputDir").asFile.absolutePath
        )
        task.systemProperty(
          "route.server.package",
          extension.serverRoutesPackage.required("serverRoutesPackage")
        )
      }
    }

    project.tasks.register("generateWebpackBundleEntries", JavaExec::class.java) { task ->
      task.description = "Generates a file containing the path to each React app's entry point."
      task.group = TASK_GROUP
      task.dependsOn(routeDefinitionClasses)
      task.classpath(routeDefinitionClasspath)
      task.mainClass.set(WEBPACK_BUNDLE_MAIN_CLASS)
      task.doFirst {
        extension.requiredRouteDefinitionsProject()
        task.systemProperty(
          "spa.application.source.dir",
          extension.applicationSourceDir.required("applicationSourceDir").asFile.absolutePath
        )
        task.systemProperty(
          "route.output.dir",
          extension.webpackBundleEntriesOutputFile.required("webpackBundleEntriesOutputFile").asFile.absolutePath
        )
      }
    }

    project.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
      project.extensions.configure(KotlinJvmProjectExtension::class.java) { kotlinExtension ->
        kotlinExtension.sourceSets.named("main") { sourceSet ->
          sourceSet.kotlin.srcDir(extension.serverRoutesSourceRoot)
        }
      }

      project.tasks.withType(KotlinCompile::class.java).configureEach { task ->
        task.dependsOn(generateServerSpaRoutes)
      }
    }
  }

  private fun Project.mainRuntimeClasspath() =
    (extensions.findByType(SourceSetContainer::class.java)
      ?: throw GradleException(
        "spaRouting.routeDefinitionsProject ($path) must apply the Java or Kotlin JVM plugin."
      ))
      .named("main")
      .get()
      .runtimeClasspath

  private fun SpaRoutingExtension.requiredRouteDefinitionsProject(): Project {
    return routeDefinitionsProject.required("routeDefinitionsProject")
  }

  private fun <T : Any> Provider<T>.required(propertyName: String): T {
    if (!isPresent) {
      throw GradleException("spaRouting.$propertyName must be set.")
    }
    return get()
  }

  private companion object {
    const val TASK_GROUP = "spa routing"
    const val DEFAULT_SERVER_ROUTES_PACKAGE = "com.caseymcguiredotcom.generated.spa.routes"
    const val CLIENT_ROUTE_MAIN_CLASS =
      "com.caseymcguiredotcom.sparoutecontract.codegen.GenerateClientRoutesKt"
    const val SERVER_ROUTE_MAIN_CLASS =
      "com.caseymcguiredotcom.sparoutecontract.codegen.GenerateServerRoutesKt"
    const val WEBPACK_BUNDLE_MAIN_CLASS =
      "com.caseymcguiredotcom.sparoutecontract.codegen.GenerateWebpackBundleEntriesKt"
  }
}
