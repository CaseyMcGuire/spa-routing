package com.sparouting.spring.testsupport

import com.sparouting.contract.AccessDecision
import com.sparouting.contract.RouteManifest
import com.sparouting.contract.SinglePageApplicationManifest
import com.sparouting.runtime.access.ApplicationAccessHandler
import com.sparouting.runtime.config.SinglePageApplicationConfig
import com.sparouting.runtime.request.RouteRequest

internal data class TestSinglePageApplicationManifest(
  override val id: String = "test",
  override val name: String = "Test",
  override val routes: List<RouteManifest>,
  override val bundleName: String = id
) : SinglePageApplicationManifest

internal data class TestSinglePageApplicationConfig(
  override val manifest: SinglePageApplicationManifest
) : SinglePageApplicationConfig

internal fun applicationAccessHandler(
  manifest: SinglePageApplicationManifest,
  evaluateAccess: (RouteRequest) -> AccessDecision = { AccessDecision.Allow }
): ApplicationAccessHandler {
  return object : ApplicationAccessHandler(manifest) {
    override fun evaluate(request: RouteRequest): AccessDecision = evaluateAccess(request)
  }
}

internal fun testRequest(): RouteRequest {
  return RouteRequest(
    applicationId = "test",
    routeId = "Route"
  )
}
