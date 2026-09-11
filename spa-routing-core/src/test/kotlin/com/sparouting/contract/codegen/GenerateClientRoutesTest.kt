package com.sparouting.contract.codegen

import com.sparouting.contract.SpaApplicationDefinition
import com.sparouting.contract.SpaRouteDefinition
import com.sparouting.contract.string
import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

class GenerateClientRoutesTest {
  @Test
  fun `includes application id and route id on generated builders`() {
    val outputDirectory = Files.createTempDirectory("spa-routing-client-routes")
    val previousSourceDirectory = System.getProperty("spa.application.source.dir")
    val previousOutputDirectory = System.getProperty("route.output.dir")

    try {
      System.setProperty("spa.application.source.dir", "src/test/kotlin")
      System.setProperty("route.output.dir", outputDirectory.toString())

      generateClientRoutes()

      val generated = outputDirectory.resolve("ClientGeneratorTestRoutes.ts").readText()

      // Route without params still carries both ids.
      assertTrue(
        generated.contains(
          "Dashboard: routeWithoutParams(\"/clientgeneratortest/dashboard\", " +
            "{ applicationId: \"clientgeneratortest\", routeId: \"Dashboard\" })"
        ),
        generated
      )
      // Parameterized route carries both ids as the final argument.
      assertTrue(
        generated.contains("{ applicationId: \"clientgeneratortest\", routeId: \"UserDetail\" }"),
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
      assertTrue(generated.contains("function encodeRouteParam(value: string): string"), generated)
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

object ClientGeneratorTestApplication : SpaApplicationDefinition {
  override val id = "clientgeneratortest"
  override val name = "ClientGeneratorTest"
  override val urlPrefix = "clientgeneratortest"
  override val appRootPath = "src/test"
  override val routes = listOf(
    SpaRouteDefinition(path = "dashboard", id = "Dashboard"),
    SpaRouteDefinition(
      path = "users/{id}",
      id = "UserDetail",
      parameters = listOf(string("id"))
    ),
    SpaRouteDefinition(
      path = "documents/{id}/{tab}",
      id = "DocumentDetail",
      parameters = listOf(string("id"), string("tab").optional())
    )
  )
}
