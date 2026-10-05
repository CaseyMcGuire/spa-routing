package com.sparouting.runtime.testsupport

import com.sparouting.contract.AccessDecision
import com.sparouting.contract.HtmlRenderer
import com.sparouting.contract.RouteAccessHandler
import com.sparouting.contract.RouteManifest
import com.sparouting.contract.ApplicationAccessHandler
import com.sparouting.contract.RouteAccessHandlers
import com.sparouting.contract.SinglePageApplicationConfig
import com.sparouting.contract.RouteRequest
import com.sparouting.runtime.rendering.HtmlDocumentRenderer

internal data class TestSinglePageApplicationConfig(
  override val id: String = "test",
  override val name: String = "Test",
  override val routes: List<RouteManifest>,
  override val bundleName: String = id,
  override val applicationAccessHandler: ApplicationAccessHandler<TestSinglePageApplicationConfig> = applicationAccessHandler(),
  override val routeAccessHandlers: RouteAccessHandlers<TestSinglePageApplicationConfig> = routeAccessHandlers(),
  override val htmlRenderer: HtmlRenderer = HtmlDocumentRenderer()
) : SinglePageApplicationConfig

internal fun applicationAccessHandler(
  evaluateAccess: (RouteRequest) -> AccessDecision = { AccessDecision.Allow }
): ApplicationAccessHandler<TestSinglePageApplicationConfig> {
  return object : ApplicationAccessHandler<TestSinglePageApplicationConfig>() {
    override fun evaluate(request: RouteRequest): AccessDecision = evaluateAccess(request)
  }
}

internal fun routeAccessHandlers(vararg handlers: RouteAccessHandler<*>): RouteAccessHandlers<TestSinglePageApplicationConfig> {
  return object : RouteAccessHandlers<TestSinglePageApplicationConfig> {
    override val handlers: List<RouteAccessHandler<*>> = handlers.toList()
  }
}

internal fun testRequest(): RouteRequest {
  return RouteRequest(
    applicationId = "test",
    routeId = "Route"
  )
}
