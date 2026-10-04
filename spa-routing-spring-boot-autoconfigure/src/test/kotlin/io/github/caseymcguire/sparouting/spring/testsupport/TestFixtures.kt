package io.github.caseymcguire.sparouting.spring.testsupport

import com.sparouting.contract.RouteDefinition
import com.sparouting.contract.SinglePageApplicationDefinition
import io.github.caseymcguire.sparouting.runtime.config.SinglePageApplicationConfig
import io.github.caseymcguire.sparouting.runtime.request.RouteRequest
import io.github.caseymcguire.sparouting.runtime.rules.RouteRule
import io.github.caseymcguire.sparouting.runtime.rules.RouteRuleResult

internal data class TestSinglePageApplicationDefinition(
  override val id: String = "test",
  override val name: String = "Test",
  override val urlPrefix: String = id,
  override val appRootPath: String = "src/main/web-frontend/apps/$id",
  override val routes: List<RouteDefinition>,
  override val bundleName: String = id
) : SinglePageApplicationDefinition

internal data class TestSinglePageApplicationConfig(
  override val application: SinglePageApplicationDefinition,
  override val rules: List<RouteRule> = emptyList()
) : SinglePageApplicationConfig

internal class RecordingRule(
  private val result: RouteRuleResult,
  private val onEvaluate: () -> Unit = {}
) : RouteRule {
  override fun evaluate(request: RouteRequest): RouteRuleResult {
    onEvaluate()
    return result
  }
}

internal fun testRequest(): RouteRequest {
  return RouteRequest(
    applicationId = "test",
    routeId = "Route",
    method = "GET",
    path = "/test/route"
  )
}
