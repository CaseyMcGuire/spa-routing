package com.sparouting.spring.web

import com.sparouting.spring.testsupport.testEvaluator
import com.sparouting.contract.AccessDecision
import com.sparouting.contract.DenialReason
import com.sparouting.contract.RouteTarget
import com.sparouting.contract.RouteManifest
import com.sparouting.contract.RouteRequest
import com.sparouting.spring.autoconfigure.RoutingProperties
import com.sparouting.spring.testsupport.applicationAccessHandler
import com.sparouting.spring.testsupport.TestSinglePageApplicationConfig
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders

private val denialReason = DenialReason(code = "authentication_required", message = "Sign in to continue.")

class RouteDecisionRouterFunctionFactoryTest {
  @Test
  fun `route decision returns allowed response`() {
    val mockMvc = mockMvc(
      TestSinglePageApplicationConfig(routes = listOf(RouteManifest("/test/users/{id}", "UserDetail")))
    )

    for (id in listOf("42", "user-42", "9223372036854775807", "0042")) {
      mockMvc.get("/__spa/route-decision") {
        param("applicationId", "test")
        param("routeId", "UserDetail")
        param("parameters.id", id)
      }.andExpect {
        status { isOk() }
        header { string("Cache-Control", "no-store") }
        jsonPath("$.type") { value("allowed") }
        jsonPath("$.destination") { doesNotExist() }
        jsonPath("$.reason") { doesNotExist() }
      }
    }
  }

  @Test
  fun `route decision returns denied response without redirecting`() {
    val mockMvc = mockMvc(
      TestSinglePageApplicationConfig(routes = listOf(RouteManifest("/test/admin", "Admin"), RouteManifest("/test/login", "Login"))),
      evaluateApplication = ::requireUserHeader
    )

    mockMvc.get("/__spa/route-decision") {
      param("applicationId", "test")
      param("routeId", "Admin")
    }.andExpect {
      status { isOk() }
      header { string("Cache-Control", "no-store") }
      jsonPath("$.type") { value("denied") }
      jsonPath("$.destination") { value("/test/login") }
      jsonPath("$.reason.code") { value(denialReason.code) }
      jsonPath("$.reason.message") { value(denialReason.message) }
    }
  }

  @Test
  fun `route decision uses real request headers`() {
    val mockMvc = mockMvc(
      TestSinglePageApplicationConfig(routes = listOf(RouteManifest("/test/admin", "Admin"))),
      evaluateApplication = ::requireUserHeader
    )

    mockMvc.get("/__spa/route-decision") {
      param("applicationId", "test")
      param("routeId", "Admin")
      header("X-User", "casey")
    }.andExpect {
      status { isOk() }
      jsonPath("$.type") { value("allowed") }
      jsonPath("$.destination") { doesNotExist() }
    }
  }

  @Test
  fun `route decision supplies recovery destination for missing params`() {
    val properties = RoutingProperties()
    val mockMvc = mockMvc(
      TestSinglePageApplicationConfig(routes = listOf(RouteManifest("/test/users/{id}", "UserDetail"))),
      properties = properties
    )

    mockMvc.get("/__spa/route-decision") {
      param("applicationId", "test")
      param("routeId", "UserDetail")
    }.andExpect {
      status { isOk() }
      jsonPath("$.type") { value("invalid_request") }
      jsonPath("$.destination") { value("/errors/invalid-request") }
      jsonPath("$.reason.code") { value("invalid_request") }
      jsonPath("$.statusCode") { doesNotExist() }
    }
  }

  @Test
  fun `route decision includes target route query parameters`() {
    val mockMvc = mockMvc(
      TestSinglePageApplicationConfig(routes = listOf(RouteManifest("/test/users/{id}", "UserDetail"))),
      evaluateApplication = { request ->
        kotlin.test.assertEquals("billing", request.queryStringValue("tab"))
        AccessDecision.Allowed
      }
    )

    mockMvc.get("/__spa/route-decision") {
      param("applicationId", "test")
      param("routeId", "UserDetail")
      param("parameters.id", "42")
      param("queryString.tab", "billing")
    }.andExpect {
      status { isOk() }
      jsonPath("$.type") { value("allowed") }
    }
  }

  @Test
  fun `route decision path is configurable`() {
    val properties = RoutingProperties()
    properties.routeDecision.path = "/internal/spa-route-decision"
    val mockMvc = mockMvc(
      TestSinglePageApplicationConfig(routes = listOf(RouteManifest("/test/home", "Home"))),
      properties = properties
    )

    mockMvc.get("/internal/spa-route-decision") {
      param("applicationId", "test")
      param("routeId", "Home")
    }.andExpect {
      status { isOk() }
      jsonPath("$.type") { value("allowed") }
    }
  }

  private fun requireUserHeader(request: RouteRequest): AccessDecision {
    return if (request.header("X-User").isEmpty()) {
      AccessDecision.Denied(reason = denialReason, destination = RouteTarget("test", "Login"))
    } else {
      AccessDecision.Allowed
    }
  }

  private fun mockMvc(
    config: TestSinglePageApplicationConfig,
    properties: RoutingProperties = RoutingProperties(),
    evaluateApplication: (RouteRequest) -> AccessDecision = { AccessDecision.Allowed }
  ): MockMvc {
    return MockMvcBuilders.routerFunctions(
      RouteDecisionRouterFunctionFactory(
        evaluator = testEvaluator(
          configs = listOf(config.copy(applicationAccessHandler = applicationAccessHandler(evaluateApplication)))
        ),
        properties = properties
      ).routes()
    ).build()
  }
}
