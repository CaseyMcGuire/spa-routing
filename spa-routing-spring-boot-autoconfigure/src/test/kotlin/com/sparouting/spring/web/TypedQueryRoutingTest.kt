package com.sparouting.spring.web

import com.sparouting.contract.AccessDecision
import com.sparouting.contract.RouteTarget
import com.sparouting.contract.parameter
import com.sparouting.contract.RouteManifest
import com.sparouting.spring.testsupport.applicationAccessHandler
import com.sparouting.runtime.access.RouteAccessEvaluator
import com.sparouting.runtime.config.SinglePageApplicationRouteRegistry
import com.sparouting.runtime.request.RouteRequest
import com.sparouting.runtime.response.RouteHttpResponse
import com.sparouting.runtime.response.RouteResponseService
import com.sparouting.spring.autoconfigure.RoutingProperties
import com.sparouting.spring.rendering.DefaultHtmlRenderer
import com.sparouting.spring.request.DefaultRouteRequestFactory
import com.sparouting.spring.testsupport.TestSinglePageApplicationConfig
import com.sparouting.spring.testsupport.TestSinglePageApplicationManifest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class TypedQueryRoutingTest {
  private val requests = mutableListOf<RouteRequest>()
  private val config = TestSinglePageApplicationConfig(
    manifest = TestSinglePageApplicationManifest(routes = listOf(
      RouteManifest("/test/users/{id}", "UserDetail", queryString = listOf(
        parameter("foo"), parameter("tag").repeated(), parameter("baz").optional(), parameter("filter").repeated().optional()
      ))
    ))
  )
  private val handler = applicationAccessHandler(config.manifest) { request ->
    requests.add(request)
    AccessDecision.Allow
  }
  private val registry = SinglePageApplicationRouteRegistry(listOf(config), listOf(handler))
  private val evaluator = RouteAccessEvaluator(registry)
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
      assertPageAndDecision(query, 400)
    }
    assertTrue(requests.isEmpty())
  }

  @Test
  fun `query error status is independently configurable for both endpoints`() {
    val properties = RoutingProperties().apply {
      server.invalidPathParameterStatus = 409
      server.invalidQueryStringStatus = 422
    }
    assertPageAndDecision(valid - "foo", 422, properties)
    assertTrue(requests.isEmpty())
  }

  @Test
  fun `service rejects empty required lists and permits empty optional lists`() {
    val service = RouteResponseService(registry, evaluator)
    assertEquals(400, service.evaluate(RouteRequest(
      "test", "UserDetail", mapOf("id" to "123"), queryString = valid + mapOf("tag" to emptyList())
    )).statusCode)
    assertEquals(200, service.evaluate(RouteRequest(
      "test", "UserDetail", mapOf("id" to "123"), queryString = valid + mapOf("filter" to emptyList())
    )).statusCode)
  }

  @Test
  fun `typed redirects encode declared and extra query parameters`() {
    val result = redirect(RouteTarget(
      "test", "UserDetail", mapOf("id" to "123"),
      queryString = valid + mapOf("baz" to listOf(""), "utm_source" to listOf("extra"))
    ))
    assertEquals(302, result.statusCode)
    assertEquals("/test/users/123?foo=a+b%2B%26%3D%E9%9B%AA&tag=x%2Fy&tag=%C3%A9&baz=&utm_source=extra", result.location)
  }

  @Test
  fun `typed redirects reject invalid query cardinality`() {
    for (query in listOf(valid - "foo", valid + mapOf("tag" to emptyList()), valid + mapOf("foo" to listOf("a", "b")))) {
      assertFailsWith<IllegalArgumentException> {
        redirect(RouteTarget(
          "test", "UserDetail", mapOf("id" to "123"), queryString = query
        ))
      }
    }
  }

  private fun redirect(target: RouteTarget): RouteHttpResponse {
    val handler = applicationAccessHandler(config.manifest) { AccessDecision.Redirect(target) }
    val routes = SinglePageApplicationRouteRegistry(listOf(config), listOf(handler))
    return RouteResponseService(routes, RouteAccessEvaluator(routes)).evaluate(RouteRequest(
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
    val service = RouteResponseService(
      routeRegistry = registry,
      accessEvaluator = evaluator,
      invalidPathParameterStatus = properties.server.invalidPathParameterStatus,
      invalidQueryStringStatus = properties.server.invalidQueryStringStatus
    )
    val mockMvc = MockMvcBuilders.routerFunctions(
      SpringRouterFunctionFactory(
        routeConfigs = listOf(config),
        routeResponseService = service,
        requestFactory = DefaultRouteRequestFactory(),
        htmlRenderer = DefaultHtmlRenderer(properties)
      ).routes(),
      RouteDecisionRouterFunctionFactory(
        responseService = service,
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
      jsonPath("$.statusCode") { value(expectedStatus) }
    }
  }
}
