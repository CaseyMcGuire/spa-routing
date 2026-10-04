package io.github.caseymcguire.sparouting.spring.response

import com.sparouting.contract.route
import io.github.caseymcguire.sparouting.spring.config.SinglePageApplicationRouteRegistry
import io.github.caseymcguire.sparouting.spring.request.RouteRequest
import io.github.caseymcguire.sparouting.spring.rules.RouteResponseEvaluator
import io.github.caseymcguire.sparouting.spring.rules.RouteRule
import io.github.caseymcguire.sparouting.spring.rules.RouteRuleAction
import io.github.caseymcguire.sparouting.spring.rules.RouteRuleActionResolver
import io.github.caseymcguire.sparouting.spring.rules.RouteRuleResult
import io.github.caseymcguire.sparouting.spring.testsupport.RecordingRule
import io.github.caseymcguire.sparouting.spring.testsupport.TestSinglePageApplicationConfig
import io.github.caseymcguire.sparouting.spring.testsupport.TestSinglePageApplicationDefinition
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
  private val evaluator = RouteResponseEvaluator(RouteRuleActionResolver(listOf(config)))
  private val service = RouteResponseService(registry, evaluator)

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
      evaluator = evaluator,
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
    val service = RouteResponseService(
      routeRegistry = SinglePageApplicationRouteRegistry(listOf(config)),
      evaluator = RouteResponseEvaluator(RouteRuleActionResolver(listOf(config)))
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
