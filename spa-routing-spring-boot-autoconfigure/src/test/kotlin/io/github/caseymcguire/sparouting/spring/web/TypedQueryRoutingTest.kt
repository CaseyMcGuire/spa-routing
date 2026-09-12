package io.github.caseymcguire.sparouting.spring.web

import com.sparouting.contract.SpaRouteTarget
import com.sparouting.contract.route
import com.sparouting.contract.string
import io.github.caseymcguire.sparouting.spring.autoconfigure.SpaRoutingProperties
import io.github.caseymcguire.sparouting.spring.config.SinglePageApplicationRouteRegistry
import io.github.caseymcguire.sparouting.spring.rendering.DefaultSpaHtmlRenderer
import io.github.caseymcguire.sparouting.spring.request.DefaultSpaRouteRequestFactory
import io.github.caseymcguire.sparouting.spring.request.SpaRouteRequest
import io.github.caseymcguire.sparouting.spring.response.SpaRouteResponseRequest
import io.github.caseymcguire.sparouting.spring.response.SpaRouteResponseService
import io.github.caseymcguire.sparouting.spring.rules.SpaRouteResponseEvaluator
import io.github.caseymcguire.sparouting.spring.rules.SpaRouteRule
import io.github.caseymcguire.sparouting.spring.rules.SpaRouteRuleAction
import io.github.caseymcguire.sparouting.spring.rules.SpaRouteRuleActionResolver
import io.github.caseymcguire.sparouting.spring.rules.SpaRouteRuleResult
import io.github.caseymcguire.sparouting.spring.testsupport.TestSinglePageApplicationConfig
import io.github.caseymcguire.sparouting.spring.testsupport.TestSpaApplicationDefinition
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TypedQueryRoutingTest {
  private val requests = mutableListOf<SpaRouteRequest>()
  private val config = TestSinglePageApplicationConfig(
    application = TestSpaApplicationDefinition(routes = listOf(
      route("users/{id}", "UserDetail", listOf(string("id")), listOf(
        string("foo"), string("tag").repeated(), string("baz").optional(), string("filter").repeated().optional()
      ))
    )),
    rules = listOf(object : SpaRouteRule {
      override fun evaluate(request: SpaRouteRequest): SpaRouteRuleResult {
        requests.add(request)
        return SpaRouteRuleResult.Allow
      }
    })
  )
  private val resolver = SpaRouteRuleActionResolver(listOf(config))
  private val evaluator = SpaRouteResponseEvaluator(resolver)
  private val registry = SinglePageApplicationRouteRegistry(listOf(config))
  private val valid = linkedMapOf("foo" to listOf("a b+&=雪"), "tag" to listOf("x/y", "é"))

  @Test
  fun `page loads and decisions preserve query values and extras for rules`() {
    val queries = valid + mapOf("baz" to listOf(""), "filter" to listOf("a", "b"), "utm_source" to listOf("extra", "extra2"))
    assertPageAndDecision(queries, 200)
    assertEquals(2, requests.size)
    requests.forEach {
      assertEquals(queries, it.queryParameters)
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
    val properties = SpaRoutingProperties().apply {
      server.invalidPathParameterStatus = 409
      server.invalidQueryParameterStatus = 422
    }
    assertPageAndDecision(valid - "foo", 422, properties)
    assertTrue(requests.isEmpty())
  }

  @Test
  fun `service rejects empty required lists and permits empty optional lists`() {
    val service = SpaRouteResponseService(registry, evaluator)
    assertEquals(400, service.evaluate(SpaRouteResponseRequest(
      "test", "UserDetail", mapOf("id" to "123"), queryParameters = valid + mapOf("tag" to emptyList())
    )).statusCode)
    assertEquals(200, service.evaluate(SpaRouteResponseRequest(
      "test", "UserDetail", mapOf("id" to "123"), queryParameters = valid + mapOf("filter" to emptyList())
    )).statusCode)
  }

  @Test
  fun `typed redirects encode declared and extra query parameters`() {
    val result = resolver.resolve(SpaRouteRuleAction.redirectTo(SpaRouteTarget(
      "test", "UserDetail", mapOf("id" to "123"),
      queryParameters = valid + mapOf("baz" to listOf(""), "utm_source" to listOf("extra"))
    )))
    assertEquals(302, result.statusCode)
    assertEquals("/test/users/123?foo=a+b%2B%26%3D%E9%9B%AA&tag=x%2Fy&tag=%C3%A9&baz=&utm_source=extra", result.location)
  }

  @Test
  fun `typed redirects reject invalid query cardinality`() {
    for (query in listOf(valid - "foo", valid + mapOf("tag" to emptyList()), valid + mapOf("foo" to listOf("a", "b")))) {
      assertFailsWith<IllegalArgumentException> {
        resolver.resolve(SpaRouteRuleAction.redirectTo(SpaRouteTarget(
          "test", "UserDetail", mapOf("id" to "123"), queryParameters = query
        )))
      }
    }
  }

  private fun assertPageAndDecision(
    query: Map<String, List<String>>,
    expectedStatus: Int,
    properties: SpaRoutingProperties = SpaRoutingProperties()
  ) {
    val mockMvc = MockMvcBuilders.routerFunctions(
      SpaRouterFunctionFactory(
        listOf(config), evaluator, DefaultSpaRouteRequestFactory(), DefaultSpaHtmlRenderer(properties), properties
      ).routes(),
      SpaRouteDecisionRouterFunctionFactory(
        SpaRouteResponseService(registry, evaluator, properties.server.invalidPathParameterStatus, properties.server.invalidQueryParameterStatus),
        properties
      ).routes()
    ).build()
    mockMvc.get("/test/users/123") {
      query.forEach { (name, values) -> param(name, *values.toTypedArray()) }
    }.andExpect { status { isEqualTo(expectedStatus) } }
    mockMvc.get("/__spa/route-decision") {
      param("applicationId", "test")
      param("routeId", "UserDetail")
      param("parameters.id", "123")
      query.forEach { (name, values) -> param("queryParameters.$name", *values.toTypedArray()) }
    }.andExpect {
      status { isOk() }
      jsonPath("$.statusCode") { value(expectedStatus) }
    }
  }
}
