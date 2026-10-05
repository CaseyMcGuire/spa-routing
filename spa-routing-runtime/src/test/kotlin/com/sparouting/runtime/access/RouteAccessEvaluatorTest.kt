package com.sparouting.runtime.access

import com.sparouting.contract.AccessDecision
import com.sparouting.contract.Route
import com.sparouting.contract.RouteAccessContext
import com.sparouting.contract.RouteAccessHandler
import com.sparouting.contract.RouteTarget
import com.sparouting.contract.RouteManifest
import com.sparouting.runtime.config.SinglePageApplicationRouteRegistry
import com.sparouting.runtime.testsupport.TestSinglePageApplicationConfig
import com.sparouting.runtime.testsupport.TestSinglePageApplicationManifest
import com.sparouting.runtime.request.RouteRequest
import com.sparouting.runtime.testsupport.applicationAccessHandler
import com.sparouting.runtime.testsupport.testRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class RouteAccessEvaluatorTest {
  private val destination = RouteTarget(applicationId = "other", routeId = "Login")

  @Test
  fun `application access is evaluated before route access`() {
    val calls = mutableListOf<String>()
    val applicationHandler = { request: RouteRequest ->
      assertEquals(testRequest(), request)
      calls.add("application")
      AccessDecision.Allow
    }
    val evaluator = evaluator(applicationHandler) { context ->
      assertEquals(testRequest().path, context.path)
      calls.add("route")
      AccessDecision.Allow
    }

    val decision = evaluator.evaluate(testRequest())

    assertEquals(AccessDecision.Allow, decision)
    assertEquals(listOf("application", "route"), calls)
  }

  @Test
  fun `application redirect prevents route access evaluation`() {
    val redirect = AccessDecision.Redirect(destination)
    val evaluator = evaluator({ redirect }) {
      error("Route handler must not run after application rejection")
    }

    val decision = evaluator.evaluate(testRequest())

    // Targets remain unresolved until the response service converts the decision.
    assertSame(redirect, decision)
  }

  @Test
  fun `route redirect is returned after the application allows access`() {
    val redirect = AccessDecision.Redirect(destination)
    val evaluator = evaluator { redirect }

    val decision = evaluator.evaluate(testRequest())

    assertSame(redirect, decision)
  }

  @Test
  fun `unflagged routes use their own application handler even when route IDs match`() {
    val redirect = AccessDecision.Redirect(destination)
    val publicConfig = TestSinglePageApplicationConfig(
      manifest = TestSinglePageApplicationManifest(id = "public", routes = listOf(RouteManifest("/public/route", "Route")))
    )
    val privateConfig = TestSinglePageApplicationConfig(
      manifest = TestSinglePageApplicationManifest(id = "private", routes = listOf(RouteManifest("/private/route", "Route")))
    )
    val registry = SinglePageApplicationRouteRegistry(
      listOf(publicConfig, privateConfig),
      listOf(applicationAccessHandler(privateConfig.manifest) { redirect }, applicationAccessHandler(publicConfig.manifest))
    )
    val evaluator = RouteAccessEvaluator(registry)

    assertEquals(AccessDecision.Allow, evaluator.evaluate(
      testRequest().copy(applicationId = "public", path = "/public/route")
    ))
    assertEquals(redirect, evaluator.evaluate(testRequest().copy(applicationId = "private", path = "/private/route")))
  }

  @Test
  fun `unknown application or route is rejected before either handler runs`() {
    val evaluator = evaluator({ error("Application handler must not run for an unknown route") }) {
      error("Route handler must not run for an unknown route")
    }

    for (request in listOf(testRequest().copy(applicationId = "unknown"), testRequest().copy(routeId = "Unknown"))) {
      assertFailsWith<IllegalArgumentException> {
        evaluator.evaluate(request)
      }
    }
  }

  private fun evaluator(
    evaluateApplication: (RouteRequest) -> AccessDecision = { AccessDecision.Allow },
    evaluateRoute: (RouteAccessContext) -> AccessDecision
  ): RouteAccessEvaluator {
    val config = TestSinglePageApplicationConfig(
      manifest = TestSinglePageApplicationManifest(
        routes = listOf(RouteManifest("/test/route", "Route", hasAccessHandler = true))
      )
    )
    val handler = object : RouteAccessHandler<RouteAccessContext>(Route("test", "Route")) {
      override fun createRequest(context: RouteAccessContext): RouteAccessContext = context

      override fun evaluate(request: RouteAccessContext): AccessDecision = evaluateRoute(request)
    }
    return RouteAccessEvaluator(SinglePageApplicationRouteRegistry(
      routeConfigs = listOf(config),
      applicationHandlers = listOf(applicationAccessHandler(config.manifest, evaluateApplication)),
      routeHandlers = listOf(handler)
    ))
  }
}
