package io.github.caseymcguire.sparouting.runtime.response

import com.sparouting.contract.route
import io.github.caseymcguire.sparouting.runtime.access.RouteAccessEvaluator
import io.github.caseymcguire.sparouting.runtime.config.SinglePageApplicationRouteRegistry
import io.github.caseymcguire.sparouting.runtime.request.RouteRequest
import io.github.caseymcguire.sparouting.runtime.rules.RouteRule
import io.github.caseymcguire.sparouting.runtime.rules.RouteRuleAction
import io.github.caseymcguire.sparouting.runtime.rules.RouteRuleActionResolver
import io.github.caseymcguire.sparouting.runtime.rules.RouteRuleResult
import io.github.caseymcguire.sparouting.runtime.testsupport.RecordingRule
import io.github.caseymcguire.sparouting.runtime.testsupport.TestSinglePageApplicationConfig
import io.github.caseymcguire.sparouting.runtime.testsupport.TestSinglePageApplicationDefinition
import kotlin.test.Test
import kotlin.test.assertEquals

class RouteResponseServiceTest {
  private val config = TestSinglePageApplicationConfig(
    application = TestSinglePageApplicationDefinition(
      routes = listOf(route("users/{id}", "UserDetail"))
    ),
    rules = listOf(RecordingRule(RouteRuleResult.Deny(RouteRuleAction.redirect("/login"))))
  )
  private val registry = SinglePageApplicationRouteRegistry(listOf(config))
  private val evaluator = RouteAccessEvaluator(registry)
  private val actionResolver = RouteRuleActionResolver(listOf(config))
  private val service = RouteResponseService(registry, evaluator, actionResolver)

  @Test
  fun `unknown app or route returns not found`() {
    val response = service.evaluate(RouteResponseRequest("missing", "UserDetail"))

    assertEquals(404, response.statusCode)
  }

  @Test
  fun `missing required params returns bad request`() {
    val response = service.evaluate(
      RouteResponseRequest("test", "UserDetail")
    )

    assertEquals(400, response.statusCode)
  }

  @Test
  fun `unknown params returns configured status`() {
    val service = RouteResponseService(
      routeRegistry = registry,
      accessEvaluator = evaluator,
      actionResolver = actionResolver,
      invalidPathParameterStatus = 422
    )

    val response = service.evaluate(
      RouteResponseRequest("test", "UserDetail", mapOf("id" to "user-42", "unknown" to "value"))
    )

    assertEquals(422, response.statusCode)
  }

  @Test
  fun `query parameters are included in evaluated request`() {
    val config = TestSinglePageApplicationConfig(
      application = TestSinglePageApplicationDefinition(
        routes = listOf(route("users/{id}", "UserDetail"))
      ),
      rules = listOf(RequireQueryParameterRule("tab", "billing"))
    )
    val registry = SinglePageApplicationRouteRegistry(listOf(config))
    val service = RouteResponseService(
      routeRegistry = registry,
      accessEvaluator = RouteAccessEvaluator(registry),
      actionResolver = RouteRuleActionResolver(listOf(config))
    )

    val response = service.evaluate(
      RouteResponseRequest(
        applicationId = "test",
        routeId = "UserDetail",
        parameters = mapOf("id" to "550e8400-e29b-41d4-a716-446655440000"),
        queryString = mapOf("tab" to listOf("billing"))
      )
    )

    assertEquals(451, response.statusCode)
  }

  @Test
  fun `valid route returns evaluator result`() {
    val response = service.evaluate(
      RouteResponseRequest("test", "UserDetail", mapOf("id" to "550e8400-e29b-41d4-a716-446655440000"))
    )

    assertEquals(302, response.statusCode)
    assertEquals("/login", response.location)
  }

  @Test
  fun `service converts access actions using the supplied resolver for both entry points`() {
    val actions = mutableListOf<RouteRuleAction>()
    val expected = RouteHttpResponse(statusCode = 307, location = "/custom-login")
    val resolver = object : RouteRuleActionResolver(listOf(config)) {
      override fun resolve(action: RouteRuleAction): RouteHttpResponse {
        actions.add(action)
        return expected
      }
    }
    val service = RouteResponseService(registry, evaluator, resolver)
    val parameters = mapOf("id" to "42")

    assertEquals(expected, service.evaluate(RouteResponseRequest("test", "UserDetail", parameters)))
    assertEquals(expected, service.evaluate(RouteRequest(
      applicationId = "test",
      routeId = "UserDetail",
      method = "GET",
      path = "/test/users/42",
      pathParameters = parameters
    )))
    assertEquals(List(2) { RouteRuleAction.redirect("/login") }, actions)
  }

  @Test
  fun `custom evaluator without an access decision is denied`() {
    val evaluator = object : RouteAccessEvaluator(registry) {
      override fun evaluate(applicationRules: List<RouteRule>, request: RouteRequest): RouteRuleResult {
        return RouteRuleResult.Skip
      }
    }
    val service = RouteResponseService(registry, evaluator, actionResolver)

    val response = service.evaluate(RouteResponseRequest("test", "UserDetail", mapOf("id" to "42")))

    assertEquals(404, response.statusCode)
  }

  private class RequireQueryParameterRule(
    private val name: String,
    private val value: String
  ) : RouteRule {
    override fun evaluate(request: RouteRequest): RouteRuleResult {
      return if (request.queryStringValue(name) == value) {
        RouteRuleResult.Deny(RouteRuleAction.status(451))
      } else {
        RouteRuleResult.Skip
      }
    }
  }
}
