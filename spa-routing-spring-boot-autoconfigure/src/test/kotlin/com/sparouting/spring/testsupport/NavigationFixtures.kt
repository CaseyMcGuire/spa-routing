package com.sparouting.spring.testsupport

import com.sparouting.contract.RouteManifest
import com.sparouting.contract.RouteTarget
import com.sparouting.contract.SinglePageApplicationConfig
import com.sparouting.runtime.evaluation.DefaultRouteFailureHandler
import com.sparouting.runtime.evaluation.RouteFailureHandler
import com.sparouting.runtime.evaluation.RouteRequestEvaluator
import com.sparouting.runtime.evaluation.RouteResult

internal val failureConfig = TestSinglePageApplicationConfig(
  id = "errors",
  routes = listOf(RouteManifest("/errors/not-found", "NotFound"), RouteManifest("/errors/invalid-request", "Invalid"))
)

internal val testFailureHandler = DefaultRouteFailureHandler(
  unknownRouteDestination = RouteTarget("errors", "NotFound"),
  invalidRequestDestination = RouteTarget("errors", "Invalid")
)

internal val unknownRouteResult = RouteResult.UnknownRoute(
  destination = "/errors/not-found"
)

internal val invalidRequestResult = RouteResult.InvalidRequest(
  destination = "/errors/invalid-request"
)

internal fun testEvaluator(
  configs: List<SinglePageApplicationConfig>,
  failureHandler: RouteFailureHandler = testFailureHandler
): RouteRequestEvaluator = RouteRequestEvaluator(
  configs = configs + failureConfig,
  failureHandler = failureHandler
)
