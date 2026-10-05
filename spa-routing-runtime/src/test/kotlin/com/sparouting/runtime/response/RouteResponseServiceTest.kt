package com.sparouting.runtime.response

import com.sparouting.contract.AccessDecision
import com.sparouting.contract.RouteTarget
import com.sparouting.contract.RouteManifest
import com.sparouting.runtime.testsupport.applicationAccessHandler
import com.sparouting.contract.RouteRequest
import com.sparouting.runtime.testsupport.TestSinglePageApplicationConfig
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RouteResponseServiceTest {
  private val config = TestSinglePageApplicationConfig(
    routes = listOf(RouteManifest("/test/users/{id}", "UserDetail"), RouteManifest("/test/login", "Login"))
  )
  private val applicationHandler = applicationAccessHandler { request ->
    if (request.routeId == "Login") {
      AccessDecision.Allow
    } else {
      AccessDecision.Redirect(RouteTarget("test", "Login"))
    }
  }
  private val configs = listOf(config.copy(applicationAccessHandler = applicationHandler))
  private val service = RouteResponseService(configs)

  @Test
  fun `invalid configs fail during service construction`() {
    val duplicate = assertFailsWith<IllegalArgumentException> {
      RouteResponseService(configs + configs)
    }
    assertContains(duplicate.message.orEmpty(), "Duplicate single page application IDs")

    val missingHandler = assertFailsWith<IllegalArgumentException> {
      RouteResponseService(listOf(config.copy(
        routes = listOf(RouteManifest("/test/private", "Private", hasAccessHandler = true))
      )))
    }
    assertContains(missingHandler.message.orEmpty(), "Missing access handler for test:Private")
  }

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
      configs = configs,
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
    val handler = applicationAccessHandler { request ->
      requests.add(request)
      AccessDecision.Allow
    }
    val service = RouteResponseService(listOf(config.copy(applicationAccessHandler = handler)))

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
    val publicConfig = TestSinglePageApplicationConfig(id = "public", routes = listOf(RouteManifest("/public", "Index")))
    val privateConfig = TestSinglePageApplicationConfig(id = "private", routes = listOf(RouteManifest("/private", "Index")))
    val publicHandler = applicationAccessHandler { request ->
      applicationsChecked.add(request.applicationId)
      AccessDecision.Allow
    }
    val privateHandler = applicationAccessHandler { request ->
      applicationsChecked.add(request.applicationId)
      AccessDecision.Redirect(RouteTarget("public", "Index"))
    }
    val service = RouteResponseService(listOf(
      publicConfig.copy(applicationAccessHandler = publicHandler),
      privateConfig.copy(applicationAccessHandler = privateHandler)
    ))

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
