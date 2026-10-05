package com.sparouting.runtime.response

import com.sparouting.contract.AccessDecision
import com.sparouting.contract.RouteTarget
import com.sparouting.contract.RouteManifest
import com.sparouting.runtime.testsupport.applicationAccessHandler
import com.sparouting.runtime.access.RouteAccessEvaluator
import com.sparouting.runtime.config.SinglePageApplicationRouteRegistry
import com.sparouting.runtime.request.RouteRequest
import com.sparouting.runtime.testsupport.TestSinglePageApplicationConfig
import com.sparouting.runtime.testsupport.TestSinglePageApplicationManifest
import kotlin.test.Test
import kotlin.test.assertEquals

class RouteResponseServiceTest {
  private val config = TestSinglePageApplicationConfig(
    manifest = TestSinglePageApplicationManifest(
      routes = listOf(RouteManifest("/test/users/{id}", "UserDetail"), RouteManifest("/test/login", "Login"))
    )
  )
  private val applicationHandler = applicationAccessHandler(config.manifest) { request ->
    if (request.routeId == "Login") {
      AccessDecision.Allow
    } else {
      AccessDecision.Redirect(RouteTarget("test", "Login"))
    }
  }
  private val registry = SinglePageApplicationRouteRegistry(listOf(config), listOf(applicationHandler))
  private val evaluator = RouteAccessEvaluator(registry)
  private val service = RouteResponseService(registry, evaluator)

  @Test
  fun `unknown app or route returns not found`() {
    assertEquals(404, service.evaluate(RouteRequest("missing", "UserDetail")).statusCode)
    assertEquals(404, service.evaluate(RouteRequest("test", "Unknown")).statusCode)
  }

  @Test
  fun `missing required params returns bad request`() {
    assertEquals(400, service.evaluate(RouteRequest("test", "UserDetail")).statusCode)
  }

  @Test
  fun `unknown params returns configured status`() {
    val service = RouteResponseService(
      routeRegistry = registry,
      accessEvaluator = evaluator,
      invalidPathParameterStatus = 422
    )

    val response = service.evaluate(
      RouteRequest("test", "UserDetail", mapOf("id" to "user-42", "unknown" to "value"))
    )

    assertEquals(422, response.statusCode)
  }

  @Test
  fun `query parameters are included in application access request`() {
    val requests = mutableListOf<RouteRequest>()
    val handler = applicationAccessHandler(config.manifest) { request ->
      requests.add(request)
      AccessDecision.Allow
    }
    val registry = SinglePageApplicationRouteRegistry(listOf(config), listOf(handler))
    val service = RouteResponseService(registry, RouteAccessEvaluator(registry))

    val response = service.evaluate(RouteRequest(
      applicationId = "test",
      routeId = "UserDetail",
      pathParameters = mapOf("id" to "42"),
      queryString = mapOf("tab" to listOf("billing"))
    ))

    assertEquals(200, response.statusCode)
    assertEquals("billing", requests.single().queryStringValue("tab"))
  }

  @Test
  fun `each application uses its own handler`() {
    val applicationsChecked = mutableListOf<String>()
    val publicConfig = TestSinglePageApplicationConfig(
      manifest = TestSinglePageApplicationManifest(id = "public", routes = listOf(RouteManifest("/public", "Index")))
    )
    val privateConfig = TestSinglePageApplicationConfig(
      manifest = TestSinglePageApplicationManifest(id = "private", routes = listOf(RouteManifest("/private", "Index")))
    )
    val publicHandler = applicationAccessHandler(publicConfig.manifest) { request ->
      applicationsChecked.add(request.applicationId)
      AccessDecision.Allow
    }
    val privateHandler = applicationAccessHandler(privateConfig.manifest) { request ->
      applicationsChecked.add(request.applicationId)
      AccessDecision.Redirect(RouteTarget("public", "Index"))
    }
    val registry = SinglePageApplicationRouteRegistry(listOf(publicConfig, privateConfig), listOf(privateHandler, publicHandler))
    val service = RouteResponseService(registry, RouteAccessEvaluator(registry))

    for (applicationId in listOf("public", "private")) {
      val expected = if (applicationId == "public") RouteHttpResponse.ok() else RouteHttpResponse.found("/public")
      assertEquals(expected, service.evaluate(RouteRequest(applicationId, "Index")))
    }
    assertEquals(listOf("public", "private"), applicationsChecked)
  }

  @Test
  fun `service resolves application redirects`() {
    val expected = RouteHttpResponse(statusCode = 302, location = "/test/login")
    val parameters = mapOf("id" to "42")

    assertEquals(expected, service.evaluate(RouteRequest(
      applicationId = "test",
      routeId = "UserDetail",
      pathParameters = parameters
    )))
    assertEquals(200, service.evaluate(RouteRequest("test", "Login")).statusCode)
  }
}
