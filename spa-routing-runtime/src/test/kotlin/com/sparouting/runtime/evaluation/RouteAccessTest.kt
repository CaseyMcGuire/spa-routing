package com.sparouting.runtime.evaluation

import com.sparouting.contract.AccessDecision
import com.sparouting.contract.Route
import com.sparouting.contract.RouteAccessContext
import com.sparouting.contract.RouteAccessHandler
import com.sparouting.contract.RouteTarget
import com.sparouting.contract.RouteManifest
import com.sparouting.runtime.testsupport.TestSinglePageApplicationConfig
import com.sparouting.contract.RouteRequest
import com.sparouting.runtime.testsupport.applicationAccessHandler
import com.sparouting.runtime.testsupport.routeAccessHandlers
import com.sparouting.runtime.testsupport.testRequest
import kotlin.test.Test
import kotlin.test.assertEquals

class RouteAccessTest {
  private val destination = RouteTarget(applicationId = "other", routeId = "Login")
  private val destinationConfig = TestSinglePageApplicationConfig(
    id = "other",
    routes = listOf(RouteManifest("/other/login", "Login"))
  )

  @Test
  fun `application access is evaluated before route access`() {
    val calls = mutableListOf<String>()
    val applicationHandler = { request: RouteRequest ->
      assertEquals(testRequest(), request)
      calls.add("application")
      AccessDecision.Allow
    }
    val evaluator = evaluator(applicationHandler) { context ->
      assertEquals("/test/route", context.path)
      assertEquals("GET", context.method)
      calls.add("route")
      AccessDecision.Allow
    }

    val decision = evaluator.evaluate(testRequest())

    assertEquals(RouteResult.Allowed, decision)
    assertEquals(listOf("application", "route"), calls)
  }

  @Test
  fun `application redirect prevents route access evaluation`() {
    val redirect = AccessDecision.Redirect(destination)
    val evaluator = evaluator({ redirect }) {
      error("Route handler must not run after application rejection")
    }

    val decision = evaluator.evaluate(testRequest())

    assertEquals(RouteResult.Redirect("/other/login"), decision)
  }

  @Test
  fun `route redirect is returned after the application allows access`() {
    val redirect = AccessDecision.Redirect(destination)
    val evaluator = evaluator { redirect }

    val decision = evaluator.evaluate(testRequest())

    assertEquals(RouteResult.Redirect("/other/login"), decision)
  }

  @Test
  fun `unflagged routes use their own application handler even when route IDs match`() {
    val redirect = AccessDecision.Redirect(destination)
    val publicConfig = TestSinglePageApplicationConfig(id = "public", routes = listOf(RouteManifest("/public/route", "Route")))
    val privateConfig = TestSinglePageApplicationConfig(id = "private", routes = listOf(RouteManifest("/private/route", "Route")))
    val evaluator = RouteRequestEvaluator(
      listOf(publicConfig, privateConfig.copy(applicationAccessHandler = applicationAccessHandler { redirect }), destinationConfig)
    )

    assertEquals(RouteResult.Allowed, evaluator.evaluate(
      testRequest().copy(applicationId = "public")
    ))
    assertEquals(RouteResult.Redirect("/other/login"), evaluator.evaluate(testRequest().copy(applicationId = "private")))
  }

  @Test
  fun `unknown application or route is rejected before either handler runs`() {
    val evaluator = evaluator({ error("Application handler must not run for an unknown route") }) {
      error("Route handler must not run for an unknown route")
    }

    for (request in listOf(testRequest().copy(applicationId = "unknown"), testRequest().copy(routeId = "Unknown"))) {
      assertEquals(RouteResult.NotFound, evaluator.evaluate(request))
    }
  }

  private fun evaluator(
    evaluateApplication: (RouteRequest) -> AccessDecision = { AccessDecision.Allow },
    evaluateRoute: (RouteAccessContext) -> AccessDecision
  ): RouteRequestEvaluator {
    val config = TestSinglePageApplicationConfig(
      routes = listOf(RouteManifest("/test/route", "Route", hasAccessHandler = true))
    )
    val handler = object : RouteAccessHandler<RouteAccessContext>(Route("test", "Route")) {
      override fun createRequest(context: RouteAccessContext): RouteAccessContext = context

      override fun evaluate(request: RouteAccessContext): AccessDecision = evaluateRoute(request)
    }
    return RouteRequestEvaluator(
      configs = listOf(config.copy(
        applicationAccessHandler = applicationAccessHandler(evaluateApplication),
        routeAccessHandlers = routeAccessHandlers(handler)
      ), destinationConfig)
    )
  }
}
