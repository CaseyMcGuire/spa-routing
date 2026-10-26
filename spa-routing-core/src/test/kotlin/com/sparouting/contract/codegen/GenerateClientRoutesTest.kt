package com.sparouting.contract.codegen

import com.sparouting.contract.SinglePageApplicationDefinition
import com.sparouting.contract.RouteDefinition
import com.sparouting.contract.parameter
import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GenerateClientRoutesTest {
  @Test
  fun `includes route metadata and shares runtime helpers across generated modules`() {
    val outputDirectory = Files.createTempDirectory("spa-routing-client-routes")
    val previousSourceDirectory = System.getProperty("spa.application.source.dir")
    val previousOutputDirectory = System.getProperty("route.output.dir")

    try {
      System.setProperty("spa.application.source.dir", "src/test/kotlin")
      System.setProperty("route.output.dir", outputDirectory.toString())

      generateClientRoutes()

      val generated = outputDirectory.resolve("ClientGeneratorTestRoutes.ts").readText()

      // Route without params carries IDs and an explicit access-handler flag.
      assertTrue(
        generated.contains(
          "Dashboard: routeWithoutParams(\"/clientgeneratortest/dashboard\", " +
            "{ applicationId: \"clientgeneratortest\", routeId: \"Dashboard\", hasAccessHandler: false })"
        ),
        generated
      )
      // Parameterized routes carry the same metadata.
      assertTrue(
        generated.contains("{ applicationId: \"clientgeneratortest\", routeId: \"UserDetail\", hasAccessHandler: false }"),
        generated
      )
      assertTrue(
        generated.contains("(params: { id: string }) => `/clientgeneratortest/users/\${encodeRouteParam(params.id)}`"),
        generated
      )
      assertTrue(
        generated.contains("(params: { id: string; tab?: string }) => " +
          "`/clientgeneratortest/documents/\${encodeRouteParam(params.id)}/\${params.tab == null ? \"\" : encodeRouteParam(params.tab)}`"),
        generated
      )
      val runtimePath = outputDirectory.resolve("route-runtime.ts")
      val runtime = runtimePath.readText()
      assertTrue(runtime.contains("export function encodeRouteParam(value: string): string"), runtime)
      Files.list(outputDirectory).use { files ->
        files.filter { it.fileName.toString().endsWith("Routes.ts") }.forEach { file ->
          val routes = file.readText()
          assertTrue(routes.contains("from \"./route-runtime.js\""), routes)
          assertFalse(routes.contains("function createRouteParser"), routes)
          assertFalse(routes.contains("function encodeRouteParam"), routes)
          assertFalse(routes.contains("function appendQueryString"), routes)
          assertFalse(routes.contains("declare const routeContextBrand"), routes)
        }
      }

      // Regeneration replaces the shared runtime and removes stale application output.
      runtimePath.writeText("stale runtime")
      val obsolete = outputDirectory.resolve("ObsoleteRoutes.ts")
      obsolete.writeText("stale routes")
      generateClientRoutes()
      assertEquals(runtime, runtimePath.readText())
      assertEquals(generated, outputDirectory.resolve("ClientGeneratorTestRoutes.ts").readText())
      assertFalse(Files.exists(obsolete))
    } finally {
      restoreProperty("spa.application.source.dir", previousSourceDirectory)
      restoreProperty("route.output.dir", previousOutputDirectory)
    }
  }

  private fun restoreProperty(name: String, value: String?) {
    if (value == null) {
      System.clearProperty(name)
    } else {
      System.setProperty(name, value)
    }
  }
}

object ClientGeneratorTestApplication : SinglePageApplicationDefinition {
  override val id = "clientgeneratortest"
  override val name = "ClientGeneratorTest"
  override val urlPrefix = "clientgeneratortest"
  override val appRootPath = "src/test"
  override val routes = listOf(
    RouteDefinition(path = "dashboard", id = "Dashboard"),
    RouteDefinition(
      path = "users/{id}",
      id = "UserDetail"
    ),
    RouteDefinition(
      path = "documents/{id}/{tab}",
      id = "DocumentDetail",
      parameters = listOf(parameter("id"), parameter("tab").optional())
    )
  )
}
