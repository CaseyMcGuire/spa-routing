package io.github.caseymcguire.sparouting.runtime.response

import com.sparouting.contract.AccessDecision
import com.sparouting.contract.RouteTarget
import com.sparouting.contract.route
import io.github.caseymcguire.sparouting.runtime.access.ApplicationAccessHandler
import io.github.caseymcguire.sparouting.runtime.access.RouteAccessEvaluator
import io.github.caseymcguire.sparouting.runtime.config.SinglePageApplicationRouteRegistry
import io.github.caseymcguire.sparouting.runtime.request.RouteRequest
import io.github.caseymcguire.sparouting.runtime.testsupport.TestSinglePageApplicationConfig
import io.github.caseymcguire.sparouting.runtime.testsupport.TestSinglePageApplicationDefinition
import kotlin.test.Test
import kotlin.test.assertEquals

class RouteResponseServiceTest {
  private val config = TestSinglePageApplicationConfig(
    application = TestSinglePageApplicationDefinition(
      routes = listOf(route("users/{id}", "UserDetail"), route("login", "Login"))
    ),
    accessHandler = ApplicationAccessHandler { request ->
      if (request.routeId == "Login") {
        AccessDecision.Allow
      } else {
        AccessDecision.Redirect(RouteTarget("test", "Login"))
      }
    }
  )
  private val registry = SinglePageApplicationRouteRegistry(listOf(config))
  private val evaluator = RouteAccessEvaluator(registry)
  private val service = RouteResponseService(registry, evaluator)

  @Test
  fun `unknown app or route returns not found`() {
    assertEquals(404, service.evaluate(RouteResponseRequest("missing", "UserDetail")).statusCode)
    assertEquals(404, service.evaluate(RouteResponseRequest("test", "Unknown")).statusCode)
  }

  @Test
  fun `missing required params returns bad request`() {
    assertEquals(400, service.evaluate(RouteResponseRequest("test", "UserDetail")).statusCode)
  }

  @Test
  fun `unknown params returns configured status`() {
    val service = RouteResponseService(
      routeRegistry = registry,
      accessEvaluator = evaluator,
      invalidPathParameterStatus = 422
    )

    val response = service.evaluate(
      RouteResponseRequest("test", "UserDetail", mapOf("id" to "user-42", "unknown" to "value"))
    )

    assertEquals(422, response.statusCode)
  }

  @Test
  fun `query parameters are included in application access request`() {
    val requests = mutableListOf<RouteRequest>()
    val config = config.copy(accessHandler = ApplicationAccessHandler { request ->
      requests.add(request)
      AccessDecision.Allow
    })
    val registry = SinglePageApplicationRouteRegistry(listOf(config))
    val service = RouteResponseService(registry, RouteAccessEvaluator(registry))

    val response = service.evaluate(RouteResponseRequest(
      applicationId = "test",
      routeId = "UserDetail",
      parameters = mapOf("id" to "42"),
      queryString = mapOf("tab" to listOf("billing"))
    ))

    assertEquals(200, response.statusCode)
    assertEquals("billing", requests.single().queryStringValue("tab"))
  }

  @Test
  fun `each application uses its own handler for both entry points`() {
    val applicationsChecked = mutableListOf<String>()
    val publicConfig = TestSinglePageApplicationConfig(
      application = TestSinglePageApplicationDefinition(id = "public", routes = listOf(route("", "Index"))),
      accessHandler = ApplicationAccessHandler { request ->
        applicationsChecked.add(request.applicationId)
        AccessDecision.Allow
      }
    )
    val privateConfig = TestSinglePageApplicationConfig(
      application = TestSinglePageApplicationDefinition(id = "private", routes = listOf(route("", "Index"))),
      accessHandler = ApplicationAccessHandler { request ->
        applicationsChecked.add(request.applicationId)
        AccessDecision.Redirect(RouteTarget("public", "Index"))
      }
    )
    val registry = SinglePageApplicationRouteRegistry(listOf(publicConfig, privateConfig))
    val service = RouteResponseService(registry, RouteAccessEvaluator(registry))

    for (applicationId in listOf("public", "private")) {
      val expected = if (applicationId == "public") RouteHttpResponse.ok() else RouteHttpResponse.found("/public")
      assertEquals(expected, service.evaluate(RouteResponseRequest(applicationId, "Index")))
      assertEquals(expected, service.evaluate(RouteRequest(applicationId, "Index", "GET", "/$applicationId")))
    }
    assertEquals(listOf("public", "public", "private", "private"), applicationsChecked)
  }

  @Test
  fun `service resolves application redirects for both entry points`() {
    val expected = RouteHttpResponse(statusCode = 302, location = "/test/login")
    val parameters = mapOf("id" to "42")

    assertEquals(expected, service.evaluate(RouteResponseRequest("test", "UserDetail", parameters)))
    assertEquals(expected, service.evaluate(RouteRequest(
      applicationId = "test",
      routeId = "UserDetail",
      method = "GET",
      path = "/test/users/42",
      pathParameters = parameters
    )))
    assertEquals(200, service.evaluate(RouteResponseRequest("test", "Login")).statusCode)
  }
}
