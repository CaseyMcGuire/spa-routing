package io.github.caseymcguire.sparouting.runtime.access

import com.sparouting.contract.AccessDecision
import com.sparouting.contract.Route
import com.sparouting.contract.RouteAccessContext
import com.sparouting.contract.RouteAccessHandler
import com.sparouting.contract.RouteTarget
import com.sparouting.contract.route
import io.github.caseymcguire.sparouting.runtime.config.SinglePageApplicationRouteRegistry
import io.github.caseymcguire.sparouting.runtime.testsupport.TestSinglePageApplicationConfig
import io.github.caseymcguire.sparouting.runtime.testsupport.TestSinglePageApplicationDefinition
import io.github.caseymcguire.sparouting.runtime.testsupport.testRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class RouteAccessEvaluatorTest {
  private val destination = RouteTarget(applicationId = "other", routeId = "Login")

  @Test
  fun `application access is evaluated before route access`() {
    val calls = mutableListOf<String>()
    val applicationHandler = ApplicationAccessHandler { request ->
      assertEquals(testRequest(), request)
      calls.add("application")
      AccessDecision.Allow
    }
    val evaluator = evaluator { context ->
      assertEquals(testRequest().path, context.path)
      calls.add("route")
      AccessDecision.Allow
    }

    val decision = evaluator.evaluate(applicationHandler, testRequest())

    assertEquals(AccessDecision.Allow, decision)
    assertEquals(listOf("application", "route"), calls)
  }

  @Test
  fun `application redirect prevents route access evaluation`() {
    val redirect = AccessDecision.Redirect(destination)
    val evaluator = evaluator { error("Route handler must not run after application rejection") }

    val decision = evaluator.evaluate(ApplicationAccessHandler { redirect }, testRequest())

    // Targets remain unresolved until the response service converts the decision.
    assertSame(redirect, decision)
  }

  @Test
  fun `route redirect is returned after the application allows access`() {
    val redirect = AccessDecision.Redirect(destination)
    val evaluator = evaluator { redirect }

    val decision = evaluator.evaluate(ApplicationAccessHandler { AccessDecision.Allow }, testRequest())

    assertSame(redirect, decision)
  }

  @Test
  fun `route without a handler still requires application allowance`() {
    val config = TestSinglePageApplicationConfig(
      application = TestSinglePageApplicationDefinition(routes = listOf(route("route", "Route")))
    )
    val evaluator = RouteAccessEvaluator(SinglePageApplicationRouteRegistry(listOf(config)))
    val redirect = AccessDecision.Redirect(destination)

    assertEquals(AccessDecision.Allow, evaluator.evaluate(
      ApplicationAccessHandler { AccessDecision.Allow }, testRequest()
    ))
    assertEquals(redirect, evaluator.evaluate(ApplicationAccessHandler { redirect }, testRequest()))
  }

  private fun evaluator(evaluateRoute: (RouteAccessContext) -> AccessDecision): RouteAccessEvaluator {
    val config = TestSinglePageApplicationConfig(
      application = TestSinglePageApplicationDefinition(
        routes = listOf(route("route", "Route", generateAccessHandler = true))
      )
    )
    val handler = object : RouteAccessHandler<RouteAccessContext>(Route("test", "Route")) {
      override fun createRequest(context: RouteAccessContext): RouteAccessContext = context

      override fun evaluate(request: RouteAccessContext): AccessDecision = evaluateRoute(request)
    }
    return RouteAccessEvaluator(
      routeRegistry = SinglePageApplicationRouteRegistry(listOf(config)),
      handlers = listOf(handler)
    )
  }
}
