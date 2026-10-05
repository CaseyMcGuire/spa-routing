package io.github.caseymcguire.sparouting.spring.testsupport

import com.sparouting.contract.AccessDecision
import com.sparouting.contract.RouteDefinition
import com.sparouting.contract.SinglePageApplicationDefinition
import io.github.caseymcguire.sparouting.runtime.access.ApplicationAccessHandler
import io.github.caseymcguire.sparouting.runtime.config.SinglePageApplicationConfig
import io.github.caseymcguire.sparouting.runtime.request.RouteRequest

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
  override val accessHandler: ApplicationAccessHandler = ApplicationAccessHandler { AccessDecision.Allow }
) : SinglePageApplicationConfig

internal class RecordingApplicationAccessHandler(
  private val result: AccessDecision,
  private val onEvaluate: () -> Unit = {}
) : ApplicationAccessHandler {
  override fun evaluate(request: RouteRequest): AccessDecision {
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
