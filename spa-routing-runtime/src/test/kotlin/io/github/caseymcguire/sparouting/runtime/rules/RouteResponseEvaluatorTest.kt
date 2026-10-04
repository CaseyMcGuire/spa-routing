package io.github.caseymcguire.sparouting.runtime.rules

import io.github.caseymcguire.sparouting.runtime.testsupport.RecordingRule
import io.github.caseymcguire.sparouting.runtime.testsupport.testRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RouteResponseEvaluatorTest {
  private val evaluator = RouteResponseEvaluator(RouteRuleActionResolver(emptyList()))

  @Test
  fun `application skip continues to later application rules`() {
    val response = evaluator.evaluate(
      applicationRules = listOf(
        RecordingRule(RouteRuleResult.Skip),
        RecordingRule(RouteRuleResult.Deny(RouteRuleAction.notFound()))
      ),
      request = testRequest()
    )

    assertEquals(404, response.statusCode)
  }

  @Test
  fun `application allow passes the gate and skips later application rules`() {
    var laterApplicationRuleEvaluated = false

    val response = evaluator.evaluate(
      applicationRules = listOf(
        RecordingRule(RouteRuleResult.Allow),
        RecordingRule(
          RouteRuleResult.Deny(RouteRuleAction.notFound()),
          onEvaluate = { laterApplicationRuleEvaluated = true }
        )
      ),
      request = testRequest()
    )

    assertEquals(200, response.statusCode)
    assertFalse(laterApplicationRuleEvaluated)
  }

  @Test
  fun `application deny returns resolved action without evaluating later application rules`() {
    var laterApplicationRuleEvaluated = false

    val response = evaluator.evaluate(
      applicationRules = listOf(
        RecordingRule(RouteRuleResult.Deny(RouteRuleAction.redirect("/login"))),
        RecordingRule(RouteRuleResult.Allow, onEvaluate = { laterApplicationRuleEvaluated = true })
      ),
      request = testRequest()
    )

    assertEquals(302, response.statusCode)
    assertEquals("/login", response.location)
    assertFalse(laterApplicationRuleEvaluated)
  }

  @Test
  fun `no application rules denies by default`() {
    val response = evaluator.evaluate(
      applicationRules = emptyList(),
      request = testRequest()
    )

    assertEquals(404, response.statusCode)
    assertTrue(response.location == null)
  }

  @Test
  fun `all application rules skipping denies by default`() {
    val response = evaluator.evaluate(
      applicationRules = listOf(RecordingRule(RouteRuleResult.Skip), RecordingRule(RouteRuleResult.Skip)),
      request = testRequest()
    )

    assertEquals(404, response.statusCode)
  }

  @Test
  fun `route without a handler is served once the gate passes`() {
    val response = evaluator.evaluate(
      applicationRules = listOf(RecordingRule(RouteRuleResult.Allow)),
      request = testRequest()
    )

    assertEquals(200, response.statusCode)
  }
}
