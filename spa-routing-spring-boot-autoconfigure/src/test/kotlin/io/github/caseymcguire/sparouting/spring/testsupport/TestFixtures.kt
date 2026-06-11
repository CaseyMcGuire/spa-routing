package io.github.caseymcguire.sparouting.spring.testsupport

import com.caseymcguiredotcom.sparoutecontract.SpaApplicationDefinition
import com.caseymcguiredotcom.sparoutecontract.SpaRouteDefinition
import com.caseymcguiredotcom.sparoutecontract.SpaRouteKey
import io.github.caseymcguire.sparouting.spring.config.SinglePageApplicationConfig
import io.github.caseymcguire.sparouting.spring.request.SpaRouteRequest
import io.github.caseymcguire.sparouting.spring.rules.SpaRouteRule
import io.github.caseymcguire.sparouting.spring.rules.SpaRouteRuleResult

internal data class TestSpaApplicationDefinition(
  override val id: String = "test",
  override val name: String = "Test",
  override val urlPrefix: String = id,
  override val appRootPath: String = "src/main/web-frontend/apps/$id",
  override val routes: List<SpaRouteDefinition>,
  override val bundleName: String = id
) : SpaApplicationDefinition

internal data class TestSpaRouteKey(
  override val applicationId: String,
  override val routeId: String
) : SpaRouteKey

internal data class TestSinglePageApplicationConfig(
  override val application: SpaApplicationDefinition,
  override val rules: List<SpaRouteRule> = emptyList(),
  override val routeRules: Map<SpaRouteKey, List<SpaRouteRule>> = emptyMap()
) : SinglePageApplicationConfig

internal class RecordingRule(
  private val result: SpaRouteRuleResult,
  private val onEvaluate: () -> Unit = {}
) : SpaRouteRule {
  override fun evaluate(request: SpaRouteRequest): SpaRouteRuleResult {
    onEvaluate()
    return result
  }
}

internal fun testRequest(): SpaRouteRequest {
  return SpaRouteRequest(
    applicationId = "test",
    routeId = "Route",
    method = "GET",
    path = "/test/route"
  )
}
