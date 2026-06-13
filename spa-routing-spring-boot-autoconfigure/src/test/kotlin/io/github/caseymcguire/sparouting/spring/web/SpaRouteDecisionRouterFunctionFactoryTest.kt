package io.github.caseymcguire.sparouting.spring.web

import com.caseymcguiredotcom.sparoutecontract.int
import com.caseymcguiredotcom.sparoutecontract.route
import io.github.caseymcguire.sparouting.spring.autoconfigure.SpaRoutingProperties
import io.github.caseymcguire.sparouting.spring.config.SinglePageApplicationRouteRegistry
import io.github.caseymcguire.sparouting.spring.request.SpaRouteRequest
import io.github.caseymcguire.sparouting.spring.response.SpaRouteResponseService
import io.github.caseymcguire.sparouting.spring.rules.SpaRouteResponseEvaluator
import io.github.caseymcguire.sparouting.spring.rules.SpaRouteRule
import io.github.caseymcguire.sparouting.spring.rules.SpaRouteRuleAction
import io.github.caseymcguire.sparouting.spring.rules.SpaRouteRuleActionResolver
import io.github.caseymcguire.sparouting.spring.rules.SpaRouteRuleResult
import io.github.caseymcguire.sparouting.spring.testsupport.TestSinglePageApplicationConfig
import io.github.caseymcguire.sparouting.spring.testsupport.TestSpaApplicationDefinition
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class SpaRouteDecisionRouterFunctionFactoryTest {
  @Test
  fun `route decision returns allowed response`() {
    val mockMvc = mockMvc(
      TestSinglePageApplicationConfig(
        TestSpaApplicationDefinition(routes = listOf(route("users/{id}", "UserDetail", listOf(int("id")))))
      )
    )

    mockMvc.get("/__spa/route-decision") {
      param("applicationId", "test")
      param("routeId", "UserDetail")
      param("parameters.id", "42")
    }.andExpect {
      status { isOk() }
      header { string("Cache-Control", "no-store") }
      jsonPath("$.statusCode") { value(200) }
      jsonPath("$.location") { doesNotExist() }
    }
  }

  @Test
  fun `route decision returns denied response without redirecting`() {
    val mockMvc = mockMvc(
      TestSinglePageApplicationConfig(
        application = TestSpaApplicationDefinition(routes = listOf(route("admin", "Admin"))),
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
        application = TestSpaApplicationDefinition(routes = listOf(route("admin", "Admin"))),
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
  fun `route decision returns configured status in body for invalid params`() {
    val properties = SpaRoutingProperties()
    properties.server.invalidPathParameterStatus = 422
    val mockMvc = mockMvc(
      TestSinglePageApplicationConfig(
        TestSpaApplicationDefinition(routes = listOf(route("users/{id}", "UserDetail", listOf(int("id")))))
      ),
      properties = properties
    )

    mockMvc.get("/__spa/route-decision") {
      param("applicationId", "test")
      param("routeId", "UserDetail")
      param("parameters.id", "not-an-int")
    }.andExpect {
      status { isOk() }
      jsonPath("$.statusCode") { value(422) }
    }
  }

  @Test
  fun `route decision includes target route query parameters`() {
    val mockMvc = mockMvc(
      TestSinglePageApplicationConfig(
        application = TestSpaApplicationDefinition(routes = listOf(route("users/{id}", "UserDetail", listOf(int("id"))))),
        rules = listOf(RequireQueryParameterRule("tab", "billing"))
      )
    )

    mockMvc.get("/__spa/route-decision") {
      param("applicationId", "test")
      param("routeId", "UserDetail")
      param("parameters.id", "42")
      param("queryParameters.tab", "billing")
    }.andExpect {
      status { isOk() }
      jsonPath("$.statusCode") { value(451) }
    }
  }

  @Test
  fun `route decision path is configurable`() {
    val properties = SpaRoutingProperties()
    properties.routeDecision.path = "/internal/spa-route-decision"
    val mockMvc = mockMvc(
      TestSinglePageApplicationConfig(
        TestSpaApplicationDefinition(routes = listOf(route("home", "Home")))
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
  ) : SpaRouteRule {
    override fun evaluate(request: SpaRouteRequest): SpaRouteRuleResult {
      return if (request.header(headerName).isEmpty()) {
        SpaRouteRuleResult.Deny(SpaRouteRuleAction.redirect("/login"))
      } else {
        SpaRouteRuleResult.Allow
      }
    }
  }

  private class RequireQueryParameterRule(
    private val name: String,
    private val value: String
  ) : SpaRouteRule {
    override fun evaluate(request: SpaRouteRequest): SpaRouteRuleResult {
      return if (request.queryParameter(name) == value) {
        SpaRouteRuleResult.Deny(SpaRouteRuleAction.status(451))
      } else {
        SpaRouteRuleResult.Skip
      }
    }
  }

  private fun mockMvc(
    config: TestSinglePageApplicationConfig,
    properties: SpaRoutingProperties = SpaRoutingProperties()
  ) = MockMvcBuilders.routerFunctions(
    SpaRouteDecisionRouterFunctionFactory(
      responseService = SpaRouteResponseService(
        routeRegistry = SinglePageApplicationRouteRegistry(listOf(config)),
        evaluator = SpaRouteResponseEvaluator(SpaRouteRuleActionResolver(listOf(config))),
        invalidPathParameterStatus = properties.server.invalidPathParameterStatus
      ),
      properties = properties
    ).routes()
  ).build()
}
