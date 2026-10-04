package io.github.caseymcguire.sparouting.spring.web

import com.sparouting.contract.route
import io.github.caseymcguire.sparouting.runtime.config.SinglePageApplicationRouteRegistry
import io.github.caseymcguire.sparouting.runtime.request.RouteRequest
import io.github.caseymcguire.sparouting.runtime.response.RouteResponseService
import io.github.caseymcguire.sparouting.runtime.rules.RouteResponseEvaluator
import io.github.caseymcguire.sparouting.runtime.rules.RouteRule
import io.github.caseymcguire.sparouting.runtime.rules.RouteRuleAction
import io.github.caseymcguire.sparouting.runtime.rules.RouteRuleActionResolver
import io.github.caseymcguire.sparouting.runtime.rules.RouteRuleResult
import io.github.caseymcguire.sparouting.spring.autoconfigure.RoutingProperties
import io.github.caseymcguire.sparouting.spring.testsupport.RecordingRule
import io.github.caseymcguire.sparouting.spring.testsupport.TestSinglePageApplicationConfig
import io.github.caseymcguire.sparouting.spring.testsupport.TestSinglePageApplicationDefinition
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class RouteDecisionRouterFunctionFactoryTest {
  @Test
  fun `route decision returns allowed response`() {
    val mockMvc = mockMvc(
      TestSinglePageApplicationConfig(
        application = TestSinglePageApplicationDefinition(routes = listOf(route("users/{id}", "UserDetail"))),
        rules = listOf(RecordingRule(RouteRuleResult.Allow))
      )
    )

    for (id in listOf("42", "user-42", "9223372036854775807", "0042")) {
      mockMvc.get("/__spa/route-decision") {
        param("applicationId", "test")
        param("routeId", "UserDetail")
        param("parameters.id", id)
      }.andExpect {
        status { isOk() }
        header { string("Cache-Control", "no-store") }
        jsonPath("$.statusCode") { value(200) }
        jsonPath("$.location") { doesNotExist() }
      }
    }
  }

  @Test
  fun `route decision returns denied response without redirecting`() {
    val mockMvc = mockMvc(
      TestSinglePageApplicationConfig(
        application = TestSinglePageApplicationDefinition(routes = listOf(route("admin", "Admin"))),
        rules = listOf(RequireHeaderRule("X-User"))
      )
    )

    mockMvc.get("/__spa/route-decision") {
      param("applicationId", "test")
      param("routeId", "Admin")
    }.andExpect {
      status { isOk() }
      header { string("Cache-Control", "no-store") }
      jsonPath("$.statusCode") { value(302) }
      jsonPath("$.location") { value("/login") }
    }
  }

  @Test
  fun `route decision uses real request headers`() {
    val mockMvc = mockMvc(
      TestSinglePageApplicationConfig(
        application = TestSinglePageApplicationDefinition(routes = listOf(route("admin", "Admin"))),
        rules = listOf(RequireHeaderRule("X-User"))
      )
    )

    mockMvc.get("/__spa/route-decision") {
      param("applicationId", "test")
      param("routeId", "Admin")
      header("X-User", "casey")
    }.andExpect {
      status { isOk() }
      jsonPath("$.statusCode") { value(200) }
      jsonPath("$.location") { doesNotExist() }
    }
  }

  @Test
  fun `route decision returns configured status in body for missing params`() {
    val properties = RoutingProperties()
    properties.server.invalidPathParameterStatus = 422
    val mockMvc = mockMvc(
      TestSinglePageApplicationConfig(
        application = TestSinglePageApplicationDefinition(routes = listOf(route("users/{id}", "UserDetail"))),
        rules = listOf(RecordingRule(RouteRuleResult.Allow))
      ),
      properties = properties
    )

    mockMvc.get("/__spa/route-decision") {
      param("applicationId", "test")
      param("routeId", "UserDetail")
    }.andExpect {
      status { isOk() }
      jsonPath("$.statusCode") { value(422) }
    }
  }

  @Test
  fun `route decision includes target route query parameters`() {
    val mockMvc = mockMvc(
      TestSinglePageApplicationConfig(
        application = TestSinglePageApplicationDefinition(routes = listOf(route("users/{id}", "UserDetail"))),
        rules = listOf(RequireQueryParameterRule("tab", "billing"))
      )
    )

    mockMvc.get("/__spa/route-decision") {
      param("applicationId", "test")
      param("routeId", "UserDetail")
      param("parameters.id", "42")
      param("queryString.tab", "billing")
    }.andExpect {
      status { isOk() }
      jsonPath("$.statusCode") { value(451) }
    }
  }

  @Test
  fun `route decision path is configurable`() {
    val properties = RoutingProperties()
    properties.routeDecision.path = "/internal/spa-route-decision"
    val mockMvc = mockMvc(
      TestSinglePageApplicationConfig(
        application = TestSinglePageApplicationDefinition(routes = listOf(route("home", "Home"))),
        rules = listOf(RecordingRule(RouteRuleResult.Allow))
      ),
      properties = properties
    )

    mockMvc.get("/internal/spa-route-decision") {
      param("applicationId", "test")
      param("routeId", "Home")
    }.andExpect {
      status { isOk() }
      jsonPath("$.statusCode") { value(200) }
    }
  }

  private class RequireHeaderRule(
    private val headerName: String
  ) : RouteRule {
    override fun evaluate(request: RouteRequest): RouteRuleResult {
      return if (request.header(headerName).isEmpty()) {
        RouteRuleResult.Deny(RouteRuleAction.redirect("/login"))
      } else {
        RouteRuleResult.Allow
      }
    }
  }

  private class RequireQueryParameterRule(
    private val name: String,
    private val value: String
  ) : RouteRule {
    override fun evaluate(request: RouteRequest): RouteRuleResult {
      return if (request.queryStringValue(name) == value) {
        RouteRuleResult.Deny(RouteRuleAction.status(451))
      } else {
        RouteRuleResult.Skip
      }
    }
  }

  private fun mockMvc(
    config: TestSinglePageApplicationConfig,
    properties: RoutingProperties = RoutingProperties()
  ) = MockMvcBuilders.routerFunctions(
    RouteDecisionRouterFunctionFactory(
      responseService = RouteResponseService(
        routeRegistry = SinglePageApplicationRouteRegistry(listOf(config)),
        evaluator = RouteResponseEvaluator(RouteRuleActionResolver(listOf(config))),
        invalidPathParameterStatus = properties.server.invalidPathParameterStatus
      ),
      properties = properties
    ).routes()
  ).build()
}
