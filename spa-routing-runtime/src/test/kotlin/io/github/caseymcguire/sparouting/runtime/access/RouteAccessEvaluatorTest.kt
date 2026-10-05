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
import io.github.caseymcguire.sparouting.runtime.request.RouteRequest
import io.github.caseymcguire.sparouting.runtime.testsupport.applicationAccessHandler
import io.github.caseymcguire.sparouting.runtime.testsupport.testRequest
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
      application = TestSinglePageApplicationDefinition(id = "public", routes = listOf(route("route", "Route")))
    )
    val privateConfig = TestSinglePageApplicationConfig(
      application = TestSinglePageApplicationDefinition(id = "private", routes = listOf(route("route", "Route")))
    )
    val registry = SinglePageApplicationRouteRegistry(
      listOf(publicConfig, privateConfig),
      listOf(applicationAccessHandler(privateConfig.application) { redirect }, applicationAccessHandler(publicConfig.application))
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
      application = TestSinglePageApplicationDefinition(
        routes = listOf(route("route", "Route", generateAccessHandler = true))
      )
    )
    val handler = object : RouteAccessHandler<RouteAccessContext>(Route("test", "Route")) {
      override fun createRequest(context: RouteAccessContext): RouteAccessContext = context

      override fun evaluate(request: RouteAccessContext): AccessDecision = evaluateRoute(request)
    }
    return RouteAccessEvaluator(SinglePageApplicationRouteRegistry(
      routeConfigs = listOf(config),
      applicationHandlers = listOf(applicationAccessHandler(config.application, evaluateApplication)),
      routeHandlers = listOf(handler)
    ))
  }
}
