package com.sparouting.contract.codegen

import com.sparouting.contract.SpaApplicationDefinition
import com.sparouting.contract.SpaRouteDefinition
import com.sparouting.contract.string
import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

class GenerateServerRoutesTest {
  @Test
  fun `respects custom generated package`() {
    val outputDirectory = Files.createTempDirectory("spa-routing-server-routes")
    val previousSourceDirectory = System.getProperty("spa.application.source.dir")
    val previousOutputDirectory = System.getProperty("route.output.dir")
    val previousServerPackage = System.getProperty("route.server.package")

    try {
      System.setProperty("spa.application.source.dir", "src/test/kotlin")
      System.setProperty("route.output.dir", outputDirectory.toString())
      System.setProperty("route.server.package", "com.example.generated")

      generateServerRoutes()

      val routesObject = outputDirectory.resolve("TestRoutes.kt")
      val routeObject = outputDirectory.resolve("test/GeneratedRoute.kt")

      assertTrue(Files.exists(routesObject))
      assertTrue(Files.exists(routeObject))
      assertTrue(routesObject.readText().contains("package com.example.generated"))
      assertTrue(routeObject.readText().contains("package com.example.generated.test"))
      assertTrue(
        routesObject.readText()
          .contains("import com.example.generated.test.GeneratedRoute as GeneratedRouteRoute")
      )
      val userRoute = outputDirectory.resolve("test/UserDetail.kt").readText()
      assertTrue(userRoute.contains("operator fun invoke(id: String): SpaRouteTarget"), userRoute)
      assertTrue(userRoute.contains("return target(mapOf(\"id\" to id))"), userRoute)

      val documentRoute = outputDirectory.resolve("test/DocumentDetail.kt").readText()
      assertTrue(
        documentRoute.contains("operator fun invoke(id: String, tab: String? = null): SpaRouteTarget"),
        documentRoute
      )
      assertTrue(documentRoute.contains("put(\"id\", id)"), documentRoute)
      assertTrue(documentRoute.contains("if (tab != null)"), documentRoute)
      assertTrue(documentRoute.contains("put(\"tab\", tab)"), documentRoute)
    } finally {
      restoreProperty("spa.application.source.dir", previousSourceDirectory)
      restoreProperty("route.output.dir", previousOutputDirectory)
      restoreProperty("route.server.package", previousServerPackage)
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

object GeneratorTestApplication : SpaApplicationDefinition {
  override val id = "test"
  override val name = "Test"
  override val urlPrefix = "test"
  override val appRootPath = "src/test"
  override val routes = listOf(
    SpaRouteDefinition(
      path = "generated",
      id = "GeneratedRoute"
    ),
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
