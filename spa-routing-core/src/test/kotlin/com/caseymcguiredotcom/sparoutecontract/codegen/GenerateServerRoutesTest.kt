package com.caseymcguiredotcom.sparoutecontract.codegen

import com.caseymcguiredotcom.sparoutecontract.SpaApplicationDefinition
import com.caseymcguiredotcom.sparoutecontract.SpaRouteDefinition
import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
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

      main()

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
    )
  )
}
