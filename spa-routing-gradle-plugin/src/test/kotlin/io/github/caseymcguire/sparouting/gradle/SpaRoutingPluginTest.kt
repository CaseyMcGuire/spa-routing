package io.github.caseymcguire.sparouting.gradle

import org.gradle.api.Project
import org.gradle.api.GradleException
import org.gradle.api.tasks.JavaExec
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testfixtures.ProjectBuilder
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class SpaRoutingPluginTest {
  @Test
  fun `registers generator tasks`() {
    val project = ProjectBuilder.builder().build()

    project.pluginManager.apply(SpaRoutingPlugin::class.java)

    assertNotNull(project.tasks.findByName("generateClientRoutes"))
    assertNotNull(project.tasks.findByName("generateServerSpaRoutes"))
    assertNotNull(project.tasks.findByName("generateWebpackBundleEntries"))
  }

  @Test
  fun `passes configured paths to java exec tasks`() {
    val project = ProjectBuilder.builder().build()
    val routeDefinitionsProject = ProjectBuilder.builder()
      .withName("spa-route-definitions")
      .withParent(project)
      .build()
    routeDefinitionsProject.pluginManager.apply("java")

    project.pluginManager.apply(SpaRoutingPlugin::class.java)
    val extension = project.extensions.getByType(SpaRoutingExtension::class.java)
    extension.routeDefinitionsProject.set(routeDefinitionsProject)
    extension.applicationSourceDir.set(
      routeDefinitionsProject.layout.projectDirectory.dir("src/main/kotlin/applications")
    )
    extension.clientRoutesOutputDir.set(project.layout.buildDirectory.dir("client-routes"))
    extension.serverRoutesOutputDir.set(project.layout.buildDirectory.dir("server-routes"))
    extension.serverRoutesPackage.set("com.example.generated.routes")
    extension.webpackBundleEntriesOutputFile.set(project.layout.buildDirectory.file("bundles.ts"))

    val clientTask = project.javaExecTask("generateClientRoutes")
    val serverTask = project.javaExecTask("generateServerSpaRoutes")
    val webpackTask = project.javaExecTask("generateWebpackBundleEntries")

    clientTask.applyFirstAction()
    serverTask.applyFirstAction()
    webpackTask.applyFirstAction()

    assertEquals(
      extension.applicationSourceDir.get().asFile.absolutePath,
      clientTask.systemProperties["spa.application.source.dir"]
    )
    assertEquals(
      extension.clientRoutesOutputDir.get().asFile.absolutePath,
      clientTask.systemProperties["route.output.dir"]
    )
    assertEquals(
      extension.serverRoutesOutputDir.get().asFile.absolutePath,
      serverTask.systemProperties["route.output.dir"]
    )
    assertEquals(
      "com.example.generated.routes",
      serverTask.systemProperties["route.server.package"]
    )
    assertEquals(
      extension.webpackBundleEntriesOutputFile.get().asFile.absolutePath,
      webpackTask.systemProperties["route.output.dir"]
    )
  }

  @Test
  fun `supports nested configuration dsl`() {
    val project = ProjectBuilder.builder().build()
    val routeDefinitionsProject = ProjectBuilder.builder()
      .withName("spa-route-definitions")
      .withParent(project)
      .build()
    routeDefinitionsProject.pluginManager.apply("java")

    project.pluginManager.apply(SpaRoutingPlugin::class.java)
    val extension = project.extensions.getByType(SpaRoutingExtension::class.java)

    extension.configuration { configuration ->
      configuration.routeDefinitions { routeDefinitions ->
        routeDefinitions.projectPath = ":spa-route-definitions"
      }
      configuration.clientRoutes { clientRoutes ->
        clientRoutes.target { target ->
          target.directory = "src/main/web-frontend/__generated__/routes"
        }
      }
      configuration.serverRoutes { serverRoutes ->
        serverRoutes.target { target ->
          target.packageName = "com.example.generated.routes"
          target.directory = "build/generated/source/spaRoutes/main"
        }
      }
      configuration.webpackBundleEntries { webpackBundleEntries ->
        webpackBundleEntries.target { target ->
          target.file = "SinglePageApplicationBundles.ts"
        }
      }
    }

    val serverTask = project.javaExecTask("generateServerSpaRoutes")
    serverTask.applyFirstAction()

    assertEquals(routeDefinitionsProject, extension.routeDefinitionsProject.get())
    assertEquals(
      routeDefinitionsProject.layout.projectDirectory
        .dir("src/main/kotlin/com/caseymcguiredotcom/sparoutecontract/applications")
        .asFile
        .absolutePath,
      extension.applicationSourceDir.get().asFile.absolutePath
    )
    assertEquals(
      project.layout.projectDirectory
        .dir("build/generated/source/spaRoutes/main/com/example/generated/routes")
        .asFile
        .absolutePath,
      extension.serverRoutesOutputDir.get().asFile.absolutePath
    )
    assertEquals("com.example.generated.routes", serverTask.systemProperties["route.server.package"])
  }

  @Test
  fun `kotlin dsl accepts nested configuration syntax`() {
    val projectDirectory = Files.createTempDirectory("spa-routing-plugin-dsl-test")
    val routeDefinitionsDirectory = projectDirectory.resolve("spa-route-definitions")
    routeDefinitionsDirectory.createDirectories()

    Files.writeString(
      projectDirectory.resolve("settings.gradle.kts"),
      """
      rootProject.name = "spa-routing-plugin-dsl-test"
      include(":spa-route-definitions")
      """.trimIndent()
    )
    Files.writeString(
      routeDefinitionsDirectory.resolve("build.gradle.kts"),
      """
      plugins {
        java
      }
      """.trimIndent()
    )
    Files.writeString(
      projectDirectory.resolve("build.gradle.kts"),
      """
      plugins {
        id("io.github.caseymcguire.spa-routing")
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
              packageName = "com.example.generated.routes"
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
      """.trimIndent()
    )

    GradleRunner.create()
      .withProjectDir(projectDirectory.toFile())
      .withArguments("help")
      .withPluginClasspath()
      .build()
  }

  @Test
  fun `fails with clear message when route definitions project is missing`() {
    val project = ProjectBuilder.builder().build()
    project.pluginManager.apply(SpaRoutingPlugin::class.java)

    val failure = assertFailsWith<GradleException> {
      project.javaExecTask("generateClientRoutes").applyFirstAction()
    }

    assertEquals("spaRouting.routeDefinitionsProject must be set.", failure.message)
  }

  @Test
  fun `fails with clear message when application source directory is missing`() {
    val project = ProjectBuilder.builder().build()
    val routeDefinitionsProject = ProjectBuilder.builder()
      .withName("spa-route-definitions")
      .withParent(project)
      .build()
    routeDefinitionsProject.pluginManager.apply("java")

    project.pluginManager.apply(SpaRoutingPlugin::class.java)
    val extension = project.extensions.getByType(SpaRoutingExtension::class.java)
    extension.routeDefinitionsProject.set(routeDefinitionsProject)
    extension.clientRoutesOutputDir.set(project.layout.buildDirectory.dir("client-routes"))

    val failure = assertFailsWith<GradleException> {
      project.javaExecTask("generateClientRoutes").applyFirstAction()
    }

    assertEquals("spaRouting.applicationSourceDir must be set.", failure.message)
  }

  private fun Project.javaExecTask(name: String): JavaExec {
    return tasks.named(name, JavaExec::class.java).get()
  }

  private fun JavaExec.applyFirstAction() {
    actions.first().execute(this)
  }
}
