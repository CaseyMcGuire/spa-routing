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
  fun `skip continues to later rules`() {
    val response = evaluator.evaluate(
      listOf(
        RecordingRule(SpaRouteRuleResult.Skip),
        RecordingRule(SpaRouteRuleResult.Deny(SpaRouteRuleAction.notFound()))
      ),
      testRequest()
    )

    assertEquals(404, response.statusCode)
  }

  @Test
  fun `allow short-circuits success`() {
    var deniedRuleEvaluated = false

    val response = evaluator.evaluate(
      listOf(
        RecordingRule(SpaRouteRuleResult.Allow),
        RecordingRule(
          SpaRouteRuleResult.Deny(SpaRouteRuleAction.notFound()),
          onEvaluate = { deniedRuleEvaluated = true }
        )
      ),
      testRequest()
    )

    assertEquals(200, response.statusCode)
    assertFalse(deniedRuleEvaluated)
  }

  @Test
  fun `deny returns resolved action`() {
    val response = evaluator.evaluate(
      listOf(RecordingRule(SpaRouteRuleResult.Deny(SpaRouteRuleAction.redirect("/login")))),
      testRequest()
    )

    assertEquals(302, response.statusCode)
    assertEquals("/login", response.location)
  }

  @Test
  fun `no rules returns ok`() {
    val response = evaluator.evaluate(emptyList(), testRequest())

    assertEquals(200, response.statusCode)
    assertTrue(response.location == null)
  }
}
