package io.github.caseymcguire.sparouting.spring.rules

import io.github.caseymcguire.sparouting.spring.testsupport.RecordingRule
import io.github.caseymcguire.sparouting.spring.testsupport.testRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpaRouteResponseEvaluatorTest {
  private val evaluator = SpaRouteResponseEvaluator(SpaRouteRuleActionResolver(emptyList()))

  @Test
  fun `application skip continues to later application rules`() {
    val response = evaluator.evaluate(
      applicationRules = listOf(
        RecordingRule(SpaRouteRuleResult.Skip),
        RecordingRule(SpaRouteRuleResult.Deny(SpaRouteRuleAction.notFound()))
      ),
      routeRules = emptyList(),
      request = testRequest()
    )

    assertEquals(404, response.statusCode)
  }

  @Test
  fun `application allow passes the gate but does not skip route rules`() {
    var laterApplicationRuleEvaluated = false

    val response = evaluator.evaluate(
      applicationRules = listOf(
        RecordingRule(SpaRouteRuleResult.Allow),
        RecordingRule(
          SpaRouteRuleResult.Deny(SpaRouteRuleAction.notFound()),
          onEvaluate = { laterApplicationRuleEvaluated = true }
        )
      ),
      routeRules = listOf(RecordingRule(SpaRouteRuleResult.Deny(SpaRouteRuleAction.status(451)))),
      request = testRequest()
    )

    assertEquals(451, response.statusCode)
    assertFalse(laterApplicationRuleEvaluated)
  }

  @Test
  fun `application deny returns resolved action without evaluating route rules`() {
    var routeRuleEvaluated = false

    val response = evaluator.evaluate(
      applicationRules = listOf(RecordingRule(SpaRouteRuleResult.Deny(SpaRouteRuleAction.redirect("/login")))),
      routeRules = listOf(RecordingRule(SpaRouteRuleResult.Allow, onEvaluate = { routeRuleEvaluated = true })),
      request = testRequest()
    )

    assertEquals(302, response.statusCode)
    assertEquals("/login", response.location)
    assertFalse(routeRuleEvaluated)
  }

  @Test
  fun `no application rules denies by default`() {
    val response = evaluator.evaluate(
      applicationRules = emptyList(),
      routeRules = listOf(RecordingRule(SpaRouteRuleResult.Allow)),
      request = testRequest()
    )

    assertEquals(404, response.statusCode)
    assertTrue(response.location == null)
  }

  @Test
  fun `all application rules skipping denies by default`() {
    val response = evaluator.evaluate(
      applicationRules = listOf(RecordingRule(SpaRouteRuleResult.Skip), RecordingRule(SpaRouteRuleResult.Skip)),
      routeRules = emptyList(),
      request = testRequest()
    )

    assertEquals(404, response.statusCode)
  }

  @Test
  fun `route rules allow by default once the gate passes`() {
    val response = evaluator.evaluate(
      applicationRules = listOf(RecordingRule(SpaRouteRuleResult.Allow)),
      routeRules = listOf(RecordingRule(SpaRouteRuleResult.Skip)),
      request = testRequest()
    )

    assertEquals(200, response.statusCode)
  }

  @Test
  fun `no route rules serves once the gate passes`() {
    val response = evaluator.evaluate(
      applicationRules = listOf(RecordingRule(SpaRouteRuleResult.Allow)),
      routeRules = emptyList(),
      request = testRequest()
    )

    assertEquals(200, response.statusCode)
  }

  @Test
  fun `route deny vetoes after skipped route rules`() {
    val response = evaluator.evaluate(
      applicationRules = listOf(RecordingRule(SpaRouteRuleResult.Allow)),
      routeRules = listOf(
        RecordingRule(SpaRouteRuleResult.Skip),
        RecordingRule(SpaRouteRuleResult.Deny(SpaRouteRuleAction.notFound()))
      ),
      request = testRequest()
    )

    assertEquals(404, response.statusCode)
  }

  @Test
  fun `route allow short-circuits later route rules`() {
    var laterRouteRuleEvaluated = false

    val response = evaluator.evaluate(
      applicationRules = listOf(RecordingRule(SpaRouteRuleResult.Allow)),
      routeRules = listOf(
        RecordingRule(SpaRouteRuleResult.Allow),
        RecordingRule(
          SpaRouteRuleResult.Deny(SpaRouteRuleAction.notFound()),
          onEvaluate = { laterRouteRuleEvaluated = true }
        )
      ),
      request = testRequest()
    )

    assertEquals(200, response.statusCode)
    assertFalse(laterRouteRuleEvaluated)
  }
}
