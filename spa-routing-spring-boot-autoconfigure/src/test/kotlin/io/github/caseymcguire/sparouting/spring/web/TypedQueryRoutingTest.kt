package io.github.caseymcguire.sparouting.spring.web

import com.sparouting.contract.RouteTarget
import com.sparouting.contract.parameter
import com.sparouting.contract.route
import io.github.caseymcguire.sparouting.runtime.access.RouteAccessEvaluator
import io.github.caseymcguire.sparouting.runtime.config.SinglePageApplicationRouteRegistry
import io.github.caseymcguire.sparouting.runtime.request.RouteRequest
import io.github.caseymcguire.sparouting.runtime.response.RouteResponseRequest
import io.github.caseymcguire.sparouting.runtime.response.RouteResponseService
import io.github.caseymcguire.sparouting.runtime.rules.RouteRule
import io.github.caseymcguire.sparouting.runtime.rules.RouteRuleAction
import io.github.caseymcguire.sparouting.runtime.rules.RouteRuleActionResolver
import io.github.caseymcguire.sparouting.runtime.rules.RouteRuleResult
import io.github.caseymcguire.sparouting.spring.autoconfigure.RoutingProperties
import io.github.caseymcguire.sparouting.spring.rendering.DefaultHtmlRenderer
import io.github.caseymcguire.sparouting.spring.request.DefaultRouteRequestFactory
import io.github.caseymcguire.sparouting.spring.testsupport.TestSinglePageApplicationConfig
import io.github.caseymcguire.sparouting.spring.testsupport.TestSinglePageApplicationDefinition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class TypedQueryRoutingTest {
  private val requests = mutableListOf<RouteRequest>()
  private val config = TestSinglePageApplicationConfig(
    application = TestSinglePageApplicationDefinition(routes = listOf(
      route("users/{id}", "UserDetail", queryString = listOf(
        parameter("foo"), parameter("tag").repeated(), parameter("baz").optional(), parameter("filter").repeated().optional()
      ))
    )),
    rules = listOf(object : RouteRule {
      override fun evaluate(request: RouteRequest): RouteRuleResult {
        requests.add(request)
        return RouteRuleResult.Allow
      }
    })
  )
  private val resolver = RouteRuleActionResolver(listOf(config))
  private val registry = SinglePageApplicationRouteRegistry(listOf(config))
  private val evaluator = RouteAccessEvaluator(registry)
  private val valid = linkedMapOf("foo" to listOf("a b+&=雪"), "tag" to listOf("x/y", "é"))

  @Test
  fun `page loads and decisions preserve query values and extras for rules`() {
    val queries = valid + mapOf("baz" to listOf(""), "filter" to listOf("a", "b"), "utm_source" to listOf("extra", "extra2"))
    assertPageAndDecision(queries, 200)
    assertEquals(2, requests.size)
    requests.forEach {
      assertEquals(queries, it.queryString)
      assertEquals("/test/users/123", it.path)
      assertEquals(mapOf("id" to "123"), it.pathParameters)
    }
  }

  @Test
  fun `empty strings count as present query values`() {
    assertPageAndDecision(mapOf("foo" to listOf(""), "tag" to listOf("")), 200)
  }

  @Test
  fun `missing and repeated scalar values fail before evaluating rules`() {
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
    val service = RouteResponseService(registry, evaluator, resolver)
    assertEquals(400, service.evaluate(RouteResponseRequest(
      "test", "UserDetail", mapOf("id" to "123"), queryString = valid + mapOf("tag" to emptyList())
    )).statusCode)
    assertEquals(200, service.evaluate(RouteResponseRequest(
      "test", "UserDetail", mapOf("id" to "123"), queryString = valid + mapOf("filter" to emptyList())
    )).statusCode)
  }

  @Test
  fun `typed redirects encode declared and extra query parameters`() {
    val result = resolver.resolve(RouteRuleAction.redirectTo(RouteTarget(
      "test", "UserDetail", mapOf("id" to "123"),
      queryString = valid + mapOf("baz" to listOf(""), "utm_source" to listOf("extra"))
    )))
    assertEquals(302, result.statusCode)
    assertEquals("/test/users/123?foo=a+b%2B%26%3D%E9%9B%AA&tag=x%2Fy&tag=%C3%A9&baz=&utm_source=extra", result.location)
  }

  @Test
  fun `typed redirects reject invalid query cardinality`() {
    for (query in listOf(valid - "foo", valid + mapOf("tag" to emptyList()), valid + mapOf("foo" to listOf("a", "b")))) {
      assertFailsWith<IllegalArgumentException> {
        resolver.resolve(RouteRuleAction.redirectTo(RouteTarget(
          "test", "UserDetail", mapOf("id" to "123"), queryString = query
        )))
      }
    }
  }

  private fun assertPageAndDecision(
    query: Map<String, List<String>>,
    expectedStatus: Int,
    properties: RoutingProperties = RoutingProperties()
  ) {
    val service = RouteResponseService(
      routeRegistry = registry,
      accessEvaluator = evaluator,
      actionResolver = resolver,
      invalidPathParameterStatus = properties.server.invalidPathParameterStatus,
      invalidQueryStringStatus = properties.server.invalidQueryStringStatus
    )
    val mockMvc = MockMvcBuilders.routerFunctions(
      RouterFunctionFactory(
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
