package com.sparouting.runtime.response

import com.sparouting.contract.AccessDecision
import com.sparouting.contract.Route
import com.sparouting.contract.RouteAccessContext
import com.sparouting.contract.RouteAccessHandler
import com.sparouting.contract.RouteTarget
import com.sparouting.contract.parameter
import com.sparouting.contract.RouteManifest
import com.sparouting.runtime.testsupport.routeAccessHandlers
import com.sparouting.runtime.testsupport.applicationAccessHandler
import com.sparouting.runtime.access.RouteAccessEvaluator
import com.sparouting.runtime.config.SinglePageApplicationRouteRegistry
import com.sparouting.contract.RouteRequest
import com.sparouting.runtime.testsupport.TestSinglePageApplicationConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class RouteEvaluationTest {
  private val routeRequest = RouteRequest(
    applicationId = "test",
    routeId = "Post",
    pathParameters = mapOf("id" to "42"),
    queryString = mapOf("view" to listOf("reader"), "tracking" to listOf("campaign")),
    headers = mapOf("X-User" to listOf("casey"))
  )

  @Test
  fun `requests are validated before invoking either access handler`() {
    val runtime = Runtime()
    val invalidRequests = listOf(
      routeRequest.copy(pathParameters = emptyMap()) to 422,
      routeRequest.copy(pathParameters = mapOf("id" to "42", "extra" to "value")) to 422,
      routeRequest.copy(queryString = mapOf("view" to listOf("one", "two"))) to 400,
      routeRequest.copy(routeId = "Unknown") to 404
    )

    for ((request, expectedStatus) in invalidRequests) {
      assertEquals(expectedStatus, runtime.service.evaluate(request).statusCode)
    }

    assertEquals(emptyList(), runtime.applicationRequests)
    assertEquals(emptyList(), runtime.handlerRequests)
  }

  @Test
  fun `application redirect stops route access evaluation`() {
    val runtime = Runtime(AccessDecision.Redirect(RouteTarget(
      applicationId = "test",
      routeId = "Missing",
      queryString = mapOf("from" to listOf("application"))
    )))
    val expected = RouteHttpResponse(statusCode = 302, location = "/test/missing?from=application")

    assertEquals(expected, runtime.service.evaluate(routeRequest))
    assertEquals(1, runtime.applicationRequests.size)
    assertEquals(emptyList(), runtime.handlerRequests)
  }

  @Test
  fun `handlers receive caller data with context resolved from the target route`() {
    val runtime = Runtime()

    assertEquals(200, runtime.service.evaluate(routeRequest).statusCode)

    assertSame(routeRequest, runtime.applicationRequests.single())
    val context = runtime.handlerRequests.single()
    assertEquals("GET", context.method)
    assertEquals("/test/posts/42", context.path)
    assertEquals(routeRequest.pathParameters, context.pathParameters)
    assertEquals(routeRequest.queryString, context.queryString)
    assertEquals(routeRequest.headers, context.headers)
    assertEquals(listOf("casey"), context.header("x-user"))
  }

  @Test
  fun `typed redirects from handlers are resolved`() {
    val runtime = Runtime()
    val request = routeRequest.copy(pathParameters = mapOf("id" to "missing"))
    val expected = RouteHttpResponse(statusCode = 302, location = "/test/missing?from=post+access")

    assertEquals(expected, runtime.service.evaluate(request))
  }

  private class Runtime(applicationDecision: AccessDecision = AccessDecision.Allow) {
    val applicationRequests = mutableListOf<RouteRequest>()
    val handlerRequests = mutableListOf<RouteAccessContext>()
    private val config = TestSinglePageApplicationConfig(routes = listOf(
      RouteManifest("/test/posts/{id}", "Post", queryString = listOf(parameter("view").optional()), hasAccessHandler = true),
      RouteManifest("/test/missing", "Missing", queryString = listOf(parameter("from")))
    ))
    private val applicationHandler = applicationAccessHandler { request ->
      applicationRequests.add(request)
      applicationDecision
    }
    private val handler = object : RouteAccessHandler<RouteAccessContext>(Route("test", "Post")) {
      override fun createRequest(context: RouteAccessContext): RouteAccessContext = context

      override fun evaluate(request: RouteAccessContext): AccessDecision {
        handlerRequests.add(request)
        if (request.pathParameters.getValue("id") == "42") {
          return AccessDecision.Allow
        }
        return AccessDecision.Redirect(RouteTarget(
          applicationId = "test",
          routeId = "Missing",
          queryString = mapOf("from" to listOf("post access"))
        ))
      }
    }
    private val routeRegistry = SinglePageApplicationRouteRegistry(listOf(config.copy(
      applicationAccessHandler = applicationHandler,
      routeAccessHandlers = routeAccessHandlers(handler)
    )))
    val service = RouteResponseService(
      routeRegistry = routeRegistry,
      accessEvaluator = RouteAccessEvaluator(routeRegistry),
      invalidPathParameterStatus = 422
    )
  }
}
