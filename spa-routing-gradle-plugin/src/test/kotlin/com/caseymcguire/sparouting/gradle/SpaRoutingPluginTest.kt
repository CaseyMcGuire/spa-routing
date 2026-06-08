package com.caseymcguire.sparouting.gradle

import org.gradle.api.Project
import org.gradle.api.GradleException
import org.gradle.api.tasks.JavaExec
import org.gradle.testfixtures.ProjectBuilder
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
