package io.github.caseymcguire.sparouting.runtime.response

import com.sparouting.contract.Route
import com.sparouting.contract.RouteAccessContext
import com.sparouting.contract.RouteAccessHandler
import com.sparouting.contract.RouteDecision
import com.sparouting.contract.RouteTarget
import com.sparouting.contract.parameter
import com.sparouting.contract.route
import io.github.caseymcguire.sparouting.runtime.access.RouteAccessEvaluator
import io.github.caseymcguire.sparouting.runtime.config.SinglePageApplicationRouteRegistry
import io.github.caseymcguire.sparouting.runtime.request.RouteRequest
import io.github.caseymcguire.sparouting.runtime.rules.RouteRule
import io.github.caseymcguire.sparouting.runtime.rules.RouteRuleAction
import io.github.caseymcguire.sparouting.runtime.rules.RouteRuleActionResolver
import io.github.caseymcguire.sparouting.runtime.rules.RouteRuleResult
import io.github.caseymcguire.sparouting.runtime.testsupport.TestSinglePageApplicationConfig
import io.github.caseymcguire.sparouting.runtime.testsupport.TestSinglePageApplicationDefinition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class RouteEvaluationTest {
  private val page = RouteRequest(
    applicationId = "test",
    routeId = "Post",
    method = "HEAD",
    path = "/proxy/test/posts/42",
    pathParameters = mapOf("id" to "42"),
    queryString = mapOf("view" to listOf("reader"), "tracking" to listOf("campaign")),
    headers = mapOf("X-User" to listOf("casey"))
  )

  @Test
  fun `both entry points validate before invoking rules or handlers`() {
    val runtime = Runtime()
    val invalidRequests = listOf(
      page.copy(pathParameters = emptyMap()) to 422,
      page.copy(pathParameters = mapOf("id" to "42", "extra" to "value")) to 422,
      page.copy(queryString = mapOf("view" to listOf("one", "two"))) to 400,
      page.copy(routeId = "Unknown") to 404
    )

    for ((request, expectedStatus) in invalidRequests) {
      assertEquals(expectedStatus, runtime.service.evaluate(request).statusCode)
      assertEquals(expectedStatus, runtime.service.evaluate(request.toDecisionRequest()).statusCode)
    }

    assertEquals(emptyList(), runtime.ruleRequests)
    assertEquals(emptyList(), runtime.handlerRequests)
  }

  @Test
  fun `both entry points stop at application denial`() {
    for ((gate, expectedStatus) in listOf(
      RouteRuleResult.Skip to 404,
      RouteRuleResult.Deny(RouteRuleAction.status(451)) to 451
    )) {
      val runtime = Runtime(gate)

      assertEquals(expectedStatus, runtime.service.evaluate(page).statusCode)
      assertEquals(expectedStatus, runtime.service.evaluate(page.toDecisionRequest()).statusCode)
      assertEquals(2, runtime.ruleRequests.size)
      assertEquals(emptyList(), runtime.handlerRequests)
    }
  }

  @Test
  fun `page metadata is preserved and navigation metadata uses the target route`() {
    val runtime = Runtime()

    assertEquals(200, runtime.service.evaluate(page).statusCode)
    assertEquals(200, runtime.service.evaluate(page.toDecisionRequest()).statusCode)

    assertSame(page, runtime.ruleRequests[0])
    assertEquals("HEAD", runtime.handlerRequests[0].method)
    assertEquals("/proxy/test/posts/42", runtime.handlerRequests[0].path)
    assertEquals("GET", runtime.handlerRequests[1].method)
    assertEquals("/test/posts/42", runtime.handlerRequests[1].path)
    runtime.handlerRequests.forEach { context ->
      assertEquals(page.pathParameters, context.pathParameters)
      assertEquals(page.queryString, context.queryString)
      assertEquals(listOf("casey"), context.header("x-user"))
    }
  }

  @Test
  fun `both entry points resolve typed redirects from handlers`() {
    val runtime = Runtime()
    val request = page.copy(pathParameters = mapOf("id" to "missing"))
    val expected = RouteHttpResponse(statusCode = 302, location = "/test/missing?from=post+access")

    assertEquals(expected, runtime.service.evaluate(request))
    assertEquals(expected, runtime.service.evaluate(request.toDecisionRequest()))
  }

  private fun RouteRequest.toDecisionRequest(): RouteResponseRequest {
    return RouteResponseRequest(
      applicationId = applicationId,
      routeId = routeId,
      parameters = pathParameters,
      queryString = queryString,
      headers = headers
    )
  }

  private class Runtime(gate: RouteRuleResult = RouteRuleResult.Allow) {
    val ruleRequests = mutableListOf<RouteRequest>()
    val handlerRequests = mutableListOf<RouteAccessContext>()
    private val config = TestSinglePageApplicationConfig(
      application = TestSinglePageApplicationDefinition(routes = listOf(
        route("posts/{id}", "Post", queryString = listOf(parameter("view").optional()), generateAccessHandler = true),
        route("missing", "Missing", queryString = listOf(parameter("from")))
      )),
      rules = listOf(object : RouteRule {
        override fun evaluate(request: RouteRequest): RouteRuleResult {
          ruleRequests.add(request)
          return gate
        }
      })
    )
    private val routeRegistry = SinglePageApplicationRouteRegistry(listOf(config))
    private val handler = object : RouteAccessHandler<RouteAccessContext>(Route("test", "Post")) {
      override fun createRequest(context: RouteAccessContext): RouteAccessContext = context

      override fun evaluate(request: RouteAccessContext): RouteDecision {
        handlerRequests.add(request)
        if (request.pathParameters.getValue("id") == "42") {
          return RouteDecision.Allow
        }
        return RouteDecision.Redirect(RouteTarget(
          applicationId = "test",
          routeId = "Missing",
          queryString = mapOf("from" to listOf("post access"))
        ))
      }
    }
    val service = RouteResponseService(
      routeRegistry = routeRegistry,
      accessEvaluator = RouteAccessEvaluator(routeRegistry, listOf(handler)),
      actionResolver = RouteRuleActionResolver(listOf(config)),
      invalidPathParameterStatus = 422
    )
  }
}
