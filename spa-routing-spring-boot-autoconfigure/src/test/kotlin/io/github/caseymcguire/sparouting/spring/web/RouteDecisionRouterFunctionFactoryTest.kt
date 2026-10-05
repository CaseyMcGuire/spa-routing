package io.github.caseymcguire.sparouting.spring.web

import com.sparouting.contract.AccessDecision
import com.sparouting.contract.RouteTarget
import io.github.caseymcguire.sparouting.runtime.access.ApplicationAccessHandler
import com.sparouting.contract.route
import io.github.caseymcguire.sparouting.runtime.access.RouteAccessEvaluator
import io.github.caseymcguire.sparouting.runtime.config.SinglePageApplicationRouteRegistry
import io.github.caseymcguire.sparouting.runtime.request.RouteRequest
import io.github.caseymcguire.sparouting.runtime.response.RouteResponseService
import io.github.caseymcguire.sparouting.spring.autoconfigure.RoutingProperties
import io.github.caseymcguire.sparouting.spring.testsupport.RecordingApplicationAccessHandler
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
        accessHandler = RecordingApplicationAccessHandler(AccessDecision.Allow)
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
        application = TestSinglePageApplicationDefinition(routes = listOf(route("admin", "Admin"), route("login", "Login"))),
        accessHandler = RequireHeaderAccess("X-User")
      )
    )

    mockMvc.get("/__spa/route-decision") {
      param("applicationId", "test")
      param("routeId", "Admin")
    }.andExpect {
      status { isOk() }
      header { string("Cache-Control", "no-store") }
      jsonPath("$.statusCode") { value(302) }
      jsonPath("$.location") { value("/test/login") }
    }
  }

  @Test
  fun `route decision uses real request headers`() {
    val mockMvc = mockMvc(
      TestSinglePageApplicationConfig(
        application = TestSinglePageApplicationDefinition(routes = listOf(route("admin", "Admin"))),
        accessHandler = RequireHeaderAccess("X-User")
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
        accessHandler = RecordingApplicationAccessHandler(AccessDecision.Allow)
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
        accessHandler = ApplicationAccessHandler { request ->
          kotlin.test.assertEquals("billing", request.queryStringValue("tab"))
          AccessDecision.Allow
        }
      )
    )

    mockMvc.get("/__spa/route-decision") {
      param("applicationId", "test")
      param("routeId", "UserDetail")
      param("parameters.id", "42")
      param("queryString.tab", "billing")
    }.andExpect {
      status { isOk() }
      jsonPath("$.statusCode") { value(200) }
    }
  }

  @Test
  fun `route decision path is configurable`() {
    val properties = RoutingProperties()
    properties.routeDecision.path = "/internal/spa-route-decision"
    val mockMvc = mockMvc(
      TestSinglePageApplicationConfig(
        application = TestSinglePageApplicationDefinition(routes = listOf(route("home", "Home"))),
        accessHandler = RecordingApplicationAccessHandler(AccessDecision.Allow)
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

  private class RequireHeaderAccess(
    private val headerName: String
  ) : ApplicationAccessHandler {
    override fun evaluate(request: RouteRequest): AccessDecision {
      return if (request.header(headerName).isEmpty()) {
        AccessDecision.Redirect(RouteTarget("test", "Login"))
      } else {
        AccessDecision.Allow
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
        accessEvaluator = RouteAccessEvaluator(SinglePageApplicationRouteRegistry(listOf(config))),
        invalidPathParameterStatus = properties.server.invalidPathParameterStatus
      ),
      properties = properties
    ).routes()
  ).build()
}
