package io.github.caseymcguire.sparouting.runtime.access

import com.sparouting.contract.Route
import com.sparouting.contract.RouteAccessContext
import com.sparouting.contract.RouteAccessHandler
import com.sparouting.contract.RouteDecision
import com.sparouting.contract.RouteTarget
import com.sparouting.contract.route
import io.github.caseymcguire.sparouting.runtime.config.SinglePageApplicationRouteRegistry
import io.github.caseymcguire.sparouting.runtime.rules.RouteRuleAction
import io.github.caseymcguire.sparouting.runtime.rules.RouteRuleResult
import io.github.caseymcguire.sparouting.runtime.testsupport.RecordingRule
import io.github.caseymcguire.sparouting.runtime.testsupport.TestSinglePageApplicationConfig
import io.github.caseymcguire.sparouting.runtime.testsupport.TestSinglePageApplicationDefinition
import io.github.caseymcguire.sparouting.runtime.testsupport.testRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RouteAccessEvaluatorTest {
  private val evaluator = RouteAccessEvaluator(SinglePageApplicationRouteRegistry(emptyList()))

  @Test
  fun `application skip continues to later application rules`() {
    val decision = evaluator.evaluate(
      applicationRules = listOf(
        RecordingRule(RouteRuleResult.Skip),
        RecordingRule(RouteRuleResult.Deny(RouteRuleAction.notFound()))
      ),
      request = testRequest()
    )

    assertEquals(RouteRuleResult.Deny(RouteRuleAction.notFound()), decision)
  }

  @Test
  fun `application allow passes the gate and skips later application rules`() {
    var laterApplicationRuleEvaluated = false

    val decision = evaluator.evaluate(
      applicationRules = listOf(
        RecordingRule(RouteRuleResult.Allow),
        RecordingRule(
          RouteRuleResult.Deny(RouteRuleAction.notFound()),
          onEvaluate = { laterApplicationRuleEvaluated = true }
        )
      ),
      request = testRequest()
    )

    assertEquals(RouteRuleResult.Allow, decision)
    assertFalse(laterApplicationRuleEvaluated)
  }

  @Test
  fun `application deny returns its action without evaluating later application rules`() {
    var laterApplicationRuleEvaluated = false

    val decision = evaluator.evaluate(
      applicationRules = listOf(
        RecordingRule(RouteRuleResult.Deny(RouteRuleAction.redirect("/login"))),
        RecordingRule(RouteRuleResult.Allow, onEvaluate = { laterApplicationRuleEvaluated = true })
      ),
      request = testRequest()
    )

    assertEquals(RouteRuleResult.Deny(RouteRuleAction.redirect("/login")), decision)
    assertFalse(laterApplicationRuleEvaluated)
  }

  @Test
  fun `no application rules denies by default`() {
    val decision = evaluator.evaluate(
      applicationRules = emptyList(),
      request = testRequest()
    )

    assertEquals(RouteRuleResult.Deny(RouteRuleAction.notFound()), decision)
  }

  @Test
  fun `all application rules skipping denies by default`() {
    val decision = evaluator.evaluate(
      applicationRules = listOf(RecordingRule(RouteRuleResult.Skip), RecordingRule(RouteRuleResult.Skip)),
      request = testRequest()
    )

    assertEquals(RouteRuleResult.Deny(RouteRuleAction.notFound()), decision)
  }

  @Test
  fun `route without a handler is served once the gate passes`() {
    val decision = evaluator.evaluate(
      applicationRules = listOf(RecordingRule(RouteRuleResult.Allow)),
      request = testRequest()
    )

    assertEquals(RouteRuleResult.Allow, decision)
  }

  @Test
  fun `application redirect targets are returned without response resolution`() {
    val action = RouteRuleAction.redirectTo(
      RouteTarget(applicationId = "other", routeId = "Login"),
      statusCode = 307
    )

    val result = evaluator.evaluate(
      applicationRules = listOf(RecordingRule(RouteRuleResult.Deny(action))),
      request = testRequest()
    )

    // The destination is deliberately absent from the registry: resolution belongs to the service.
    assertEquals(RouteRuleResult.Deny(action), result)
  }

  @Test
  fun `handler redirect targets are returned without response resolution`() {
    val config = TestSinglePageApplicationConfig(
      application = TestSinglePageApplicationDefinition(
        routes = listOf(route("route", "Route", generateAccessHandler = true))
      )
    )
    val destination = RouteTarget(applicationId = "other", routeId = "Login")
    val handler = object : RouteAccessHandler<RouteAccessContext>(Route("test", "Route")) {
      override fun createRequest(context: RouteAccessContext): RouteAccessContext = context

      override fun evaluate(request: RouteAccessContext): RouteDecision {
        return RouteDecision.Redirect(destination)
      }
    }
    val evaluator = RouteAccessEvaluator(
      routeRegistry = SinglePageApplicationRouteRegistry(listOf(config)),
      handlers = listOf(handler)
    )

    val result = evaluator.evaluate(
      applicationRules = listOf(RecordingRule(RouteRuleResult.Allow)),
      request = testRequest()
    )

    assertEquals(RouteRuleResult.Deny(RouteRuleAction.redirectTo(destination)), result)
  }
}
