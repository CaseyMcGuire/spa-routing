package io.github.caseymcguire.sparouting.spring.response

import com.caseymcguiredotcom.sparoutecontract.int
import com.caseymcguiredotcom.sparoutecontract.route
import io.github.caseymcguire.sparouting.spring.config.SinglePageApplicationRouteRegistry
import io.github.caseymcguire.sparouting.spring.request.SpaRouteRequest
import io.github.caseymcguire.sparouting.spring.rules.SpaRouteResponseEvaluator
import io.github.caseymcguire.sparouting.spring.rules.SpaRouteRule
import io.github.caseymcguire.sparouting.spring.rules.SpaRouteRuleAction
import io.github.caseymcguire.sparouting.spring.rules.SpaRouteRuleActionResolver
import io.github.caseymcguire.sparouting.spring.rules.SpaRouteRuleResult
import io.github.caseymcguire.sparouting.spring.testsupport.RecordingRule
import io.github.caseymcguire.sparouting.spring.testsupport.TestSinglePageApplicationConfig
import io.github.caseymcguire.sparouting.spring.testsupport.TestSpaApplicationDefinition
import io.github.caseymcguire.sparouting.spring.testsupport.TestSpaRouteKey
import kotlin.test.Test
import kotlin.test.assertEquals

class SpaRouteResponseServiceTest {
  private val config = TestSinglePageApplicationConfig(
    application = TestSpaApplicationDefinition(
      routes = listOf(route("users/{id}", "UserDetail", listOf(int("id"))))
    ),
    routeRules = mapOf(
      TestSpaRouteKey("test", "UserDetail") to listOf(
        RecordingRule(SpaRouteRuleResult.Deny(SpaRouteRuleAction.redirect("/login")))
      )
    )
  )
  private val registry = SinglePageApplicationRouteRegistry(listOf(config))
  private val evaluator = SpaRouteResponseEvaluator(SpaRouteRuleActionResolver(listOf(config)))
  private val service = SpaRouteResponseService(registry, evaluator)

  @Test
  fun `unknown app or route returns not found`() {
    val response = service.evaluate(SpaRouteResponseRequest("missing", "UserDetail"))

    assertEquals(404, response.statusCode)
  }

  @Test
  fun `invalid params returns bad request`() {
    val response = service.evaluate(
      SpaRouteResponseRequest("test", "UserDetail", mapOf("id" to "not-an-int"))
    )

    assertEquals(400, response.statusCode)
  }

  @Test
  fun `invalid params returns configured status`() {
    val service = SpaRouteResponseService(
      routeRegistry = registry,
      evaluator = evaluator,
      invalidPathParameterStatus = 422
    )

    val response = service.evaluate(
      SpaRouteResponseRequest("test", "UserDetail", mapOf("id" to "not-an-int"))
    )

    assertEquals(422, response.statusCode)
  }

  @Test
  fun `query parameters are included in evaluated request`() {
    val config = TestSinglePageApplicationConfig(
      application = TestSpaApplicationDefinition(
        routes = listOf(route("users/{id}", "UserDetail", listOf(int("id"))))
      ),
      rules = listOf(RequireQueryParameterRule("tab", "billing"))
    )
    val service = SpaRouteResponseService(
      routeRegistry = SinglePageApplicationRouteRegistry(listOf(config)),
      evaluator = SpaRouteResponseEvaluator(SpaRouteRuleActionResolver(listOf(config)))
    )

    val response = service.evaluate(
      SpaRouteResponseRequest(
        applicationId = "test",
        routeId = "UserDetail",
        parameters = mapOf("id" to "42"),
        queryParameters = mapOf("tab" to listOf("billing"))
      )
    )

    assertEquals(451, response.statusCode)
  }

  @Test
  fun `valid route returns evaluator result`() {
    val response = service.evaluate(
      SpaRouteResponseRequest("test", "UserDetail", mapOf("id" to "42"))
    )

    assertEquals(302, response.statusCode)
    assertEquals("/login", response.location)
  }

  private class RequireQueryParameterRule(
    private val name: String,
    private val value: String
  ) : SpaRouteRule {
    override fun evaluate(request: SpaRouteRequest): SpaRouteRuleResult {
      return if (request.queryParameter(name) == value) {
        SpaRouteRuleResult.Deny(SpaRouteRuleAction.status(451))
      } else {
        SpaRouteRuleResult.Skip
      }
    }
  }
}
