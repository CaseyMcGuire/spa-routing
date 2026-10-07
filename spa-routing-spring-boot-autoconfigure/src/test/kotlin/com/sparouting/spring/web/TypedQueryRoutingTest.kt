package com.sparouting.spring.web

import com.sparouting.spring.testsupport.invalidRequestResult
import com.sparouting.spring.testsupport.testEvaluator
import com.sparouting.contract.AccessDecision
import com.sparouting.contract.DenialReason
import com.sparouting.contract.RouteTarget
import com.sparouting.contract.parameter
import com.sparouting.contract.RouteManifest
import com.sparouting.spring.testsupport.applicationAccessHandler
import com.sparouting.contract.RouteRequest
import com.sparouting.runtime.evaluation.RouteResult
import com.sparouting.runtime.response.DefaultRouteHttpResponseConverter
import com.sparouting.spring.autoconfigure.RoutingProperties
import com.sparouting.spring.request.DefaultRouteRequestFactory
import com.sparouting.spring.testsupport.TestSinglePageApplicationConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders

private val denialReason = DenialReason(code = "access_denied", message = "You cannot view this route.")

class TypedQueryRoutingTest {
  private val requests = mutableListOf<RouteRequest>()
  private val config = TestSinglePageApplicationConfig(routes = listOf(
    RouteManifest("/test/users/{id}", "UserDetail", queryString = listOf(
      parameter("foo"), parameter("tag").repeated(), parameter("baz").optional(), parameter("filter").repeated().optional()
    ))
  ))
  private val handler = applicationAccessHandler { request ->
    requests.add(request)
    AccessDecision.Allowed
  }
  private val configs = listOf(config.copy(applicationAccessHandler = handler))
  private val valid = linkedMapOf("foo" to listOf("a b+&=雪"), "tag" to listOf("x/y", "é"))

  @Test
  fun `page loads and decisions preserve query values and extras for application access`() {
    val queries = valid + mapOf("baz" to listOf(""), "filter" to listOf("a", "b"), "utm_source" to listOf("extra", "extra2"))
    assertPageAndDecision(queries, 200)
    assertEquals(2, requests.size)
    assertEquals(requests[0], requests[1])
    requests.forEach {
      assertEquals("test", it.applicationId)
      assertEquals("UserDetail", it.routeId)
      assertEquals(queries, it.queryString)
      assertEquals(mapOf("id" to "123"), it.pathParameters)
    }
  }

  @Test
  fun `empty strings count as present query values`() {
    assertPageAndDecision(mapOf("foo" to listOf(""), "tag" to listOf("")), 200)
  }

  @Test
  fun `missing and repeated scalar values fail before either access check`() {
    for (query in listOf(
      emptyMap(), valid - "foo", valid - "tag",
      valid + mapOf("foo" to listOf("a", "b")),
      valid + mapOf("baz" to listOf("a", "b"))
    )) {
      assertPageAndDecision(query, 302)
    }
    assertTrue(requests.isEmpty())
  }

  @Test
  fun `evaluator rejects empty required lists and permits empty optional lists`() {
    val evaluator = testEvaluator(configs)
    assertEquals(invalidRequestResult, evaluator.evaluate(RouteRequest(
      "test", "UserDetail", mapOf("id" to "123"), queryString = valid + mapOf("tag" to emptyList())
    )))
    assertEquals(RouteResult.Allowed, evaluator.evaluate(RouteRequest(
      "test", "UserDetail", mapOf("id" to "123"), queryString = valid + mapOf("filter" to emptyList())
    )))
  }

  @Test
  fun `typed denial destinations encode declared and extra query parameters`() {
    val result = evaluateDenial(RouteTarget(
      "test", "UserDetail", mapOf("id" to "123"),
      queryString = valid + mapOf("baz" to listOf(""), "utm_source" to listOf("extra"))
    ))
    assertEquals(
      RouteResult.Denied(
        reason = denialReason,
        destination = "/test/users/123?foo=a+b%2B%26%3D%E9%9B%AA&tag=x%2Fy&tag=%C3%A9&baz=&utm_source=extra"
      ),
      result
    )
  }

  @Test
  fun `typed denial destinations reject invalid query cardinality`() {
    for (query in listOf(valid - "foo", valid + mapOf("tag" to emptyList()), valid + mapOf("foo" to listOf("a", "b")))) {
      assertFailsWith<IllegalArgumentException> {
        evaluateDenial(RouteTarget(
          "test", "UserDetail", mapOf("id" to "123"), queryString = query
        ))
      }
    }
  }

  private fun evaluateDenial(target: RouteTarget): RouteResult {
    val handler = applicationAccessHandler { AccessDecision.Denied(reason = denialReason, destination = target) }
    return testEvaluator(listOf(config.copy(applicationAccessHandler = handler))).evaluate(RouteRequest(
      applicationId = "test",
      routeId = "UserDetail",
      pathParameters = mapOf("id" to "123"),
      queryString = valid
    ))
  }

  private fun assertPageAndDecision(
    query: Map<String, List<String>>,
    expectedStatus: Int,
    properties: RoutingProperties = RoutingProperties()
  ) {
    val evaluator = testEvaluator(configs)
    val responseConverter = DefaultRouteHttpResponseConverter()
    val mockMvc = MockMvcBuilders.routerFunctions(
      SpringRouterFunctionFactory(
        routeConfigs = configs,
        evaluator = evaluator,
        requestFactory = DefaultRouteRequestFactory(),
        responseConverter = responseConverter
      ).routes(),
      RouteDecisionRouterFunctionFactory(
        evaluator = evaluator,
        properties = properties
      ).routes()
    ).build()
    mockMvc.get("/test/users/123") {
      query.forEach { (name, values) -> param(name, *values.toTypedArray()) }
    }.andExpect { status { isEqualTo(expectedStatus) } }
    mockMvc.get("/__spa/route-decision") {
      param("applicationId", "test")
      param("routeId", "UserDetail")
      param("parameters.id", "123")
      query.forEach { (name, values) -> param("queryString.$name", *values.toTypedArray()) }
    }.andExpect {
      status { isOk() }
      jsonPath("$.type") { value(if (expectedStatus == 200) "allowed" else "invalid_request") }
      jsonPath("$.statusCode") { doesNotExist() }
      if (expectedStatus != 200) {
        jsonPath("$.destination") { value("/errors/invalid-request") }
      }
    }
  }
}
