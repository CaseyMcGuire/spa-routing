package com.sparouting.runtime.evaluation

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

class RouteRequestEvaluatorTest {
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
  private val evaluator = RouteRequestEvaluator(configs)

  @Test
  fun `invalid configs fail during evaluator construction`() {
    val duplicate = assertFailsWith<IllegalArgumentException> {
      RouteRequestEvaluator(configs + configs)
    }
    assertContains(duplicate.message.orEmpty(), "Duplicate single page application IDs")

    val missingHandler = assertFailsWith<IllegalArgumentException> {
      RouteRequestEvaluator(listOf(config.copy(
        routes = listOf(RouteManifest("/test/private", "Private", hasAccessHandler = true))
      )))
    }
    assertContains(missingHandler.message.orEmpty(), "Missing access handler for test:Private")
  }

  @Test
  fun `unknown app or route returns not found`() {
    assertEquals(RouteResult.NotFound, evaluator.evaluate(RouteRequest("missing", "UserDetail")))
    assertEquals(RouteResult.NotFound, evaluator.evaluate(RouteRequest("test", "Unknown")))
  }

  @Test
  fun `missing required params returns invalid path parameters`() {
    assertEquals(RouteResult.InvalidPathParameters, evaluator.evaluate(RouteRequest("test", "UserDetail")))
  }

  @Test
  fun `unknown params returns invalid path parameters`() {
    val response = evaluator.evaluate(
      RouteRequest("test", "UserDetail", mapOf("id" to "user-42", "unknown" to "value"))
    )

    assertEquals(RouteResult.InvalidPathParameters, response)
  }

  @Test
  fun `query parameters are included in application access request`() {
    val requests = mutableListOf<RouteRequest>()
    val handler = applicationAccessHandler { request ->
      requests.add(request)
      AccessDecision.Allow
    }
    val evaluator = RouteRequestEvaluator(listOf(config.copy(applicationAccessHandler = handler)))

    val response = evaluator.evaluate(RouteRequest(
      applicationId = "test",
      routeId = "UserDetail",
      pathParameters = mapOf("id" to "42"),
      queryString = mapOf("tab" to listOf("billing"))
    ))

    assertEquals(RouteResult.Allowed, response)
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
    val evaluator = RouteRequestEvaluator(listOf(
      publicConfig.copy(applicationAccessHandler = publicHandler),
      privateConfig.copy(applicationAccessHandler = privateHandler)
    ))

    for (applicationId in listOf("public", "private")) {
      val expected = if (applicationId == "public") RouteResult.Allowed else RouteResult.Redirect("/public")
      assertEquals(expected, evaluator.evaluate(RouteRequest(applicationId, "Index")))
    }
    assertEquals(listOf("public", "private"), applicationsChecked)
  }

  @Test
  fun `evaluator resolves application redirects`() {
    val expected = RouteResult.Redirect("/test/login")
    val parameters = mapOf("id" to "42")

    assertEquals(expected, evaluator.evaluate(RouteRequest(
      applicationId = "test",
      routeId = "UserDetail",
      pathParameters = parameters
    )))
    assertEquals(RouteResult.Allowed, evaluator.evaluate(RouteRequest("test", "Login")))
  }
}
