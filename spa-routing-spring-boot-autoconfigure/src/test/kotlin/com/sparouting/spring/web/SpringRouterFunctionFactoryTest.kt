package com.sparouting.spring.web

import com.sparouting.contract.AccessDecision
import com.sparouting.contract.RouteTarget
import com.sparouting.contract.RouteManifest
import com.sparouting.contract.SinglePageApplicationConfig
import com.sparouting.runtime.response.RouteResponseService
import com.sparouting.spring.autoconfigure.RoutingProperties
import com.sparouting.spring.config.SpringSinglePageApplicationConfig
import com.sparouting.spring.rendering.DefaultHtmlRenderer
import com.sparouting.spring.request.DefaultRouteRequestFactory
import com.sparouting.spring.testsupport.applicationAccessHandler
import com.sparouting.spring.testsupport.TestSinglePageApplicationConfig
import kotlin.test.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.servlet.function.ServerResponse

class SpringRouterFunctionFactoryTest {
  @Test
  fun `known route returns html`() {
    val mockMvc = mockMvc(
      TestSinglePageApplicationConfig(routes = listOf(RouteManifest("/test/users/{id}", "UserDetail")))
    )

    for (id in listOf("42", "user-42", "9223372036854775807", "0042")) {
      mockMvc.get("/test/users/$id")
        .andExpect {
          status { isOk() }
          content { string(org.hamcrest.Matchers.containsString("<div id=\"root\"></div>")) }
        }
    }
  }

  @Test
  fun `missing path segment returns not found`() {
    val mockMvc = mockMvc(
      TestSinglePageApplicationConfig(routes = listOf(RouteManifest("/test/users/{id}", "UserDetail")))
    )

    mockMvc.get("/test/users")
      .andExpect {
        status { isNotFound() }
      }
  }

  @Test
  fun `application redirect returns response instead of html`() {
    val mockMvc = mockMvc(
      TestSinglePageApplicationConfig(
        routes = listOf(RouteManifest("/test/admin", "Admin"), RouteManifest("/test/login", "Login")),
        applicationAccessHandler = applicationAccessHandler { AccessDecision.Redirect(RouteTarget("test", "Login")) }
      )
    )

    mockMvc.get("/test/admin")
      .andExpect {
        status { isFound() }
        header { string("Location", "/test/login") }
      }
  }

  @Test
  fun `unflagged route still checks application access`() {
    val mockMvc = mockMvc(
      TestSinglePageApplicationConfig(
        routes = listOf(RouteManifest("/test/settings", "Settings"), RouteManifest("/test/login", "Login")),
        applicationAccessHandler = applicationAccessHandler { AccessDecision.Redirect(RouteTarget("test", "Login")) }
      )
    )

    mockMvc.get("/test/settings")
      .andExpect {
        status { isFound() }
        header { string("Location", "/test/login") }
      }
  }

  @Test
  fun `spring config can render an application-specific response after access is allowed`() {
    val config = object : SpringSinglePageApplicationConfig,
      SinglePageApplicationConfig by TestSinglePageApplicationConfig(routes = listOf(RouteManifest("/test", "Index"))) {

      override fun renderHtml(): ServerResponse = ServerResponse.ok().body("Custom HTML")
    }

    mockMvc(config).get("/test").andExpect {
      status { isOk() }
      content { string("Custom HTML") }
    }
  }

  @Test
  fun `application-specific rendering is skipped when the application redirects`() {
    var rendered = false
    val config = object : SpringSinglePageApplicationConfig,
      SinglePageApplicationConfig by TestSinglePageApplicationConfig(
        routes = listOf(RouteManifest("/test", "Index"), RouteManifest("/test/login", "Login")),
        applicationAccessHandler = applicationAccessHandler { AccessDecision.Redirect(RouteTarget("test", "Login")) }
      ) {
      override fun renderHtml(): ServerResponse {
        rendered = true
        return ServerResponse.ok().body("Custom HTML")
      }
    }

    mockMvc(config)
      .get("/test").andExpect { status { isFound() } }
    assertFalse(rendered)
  }

  @Test
  fun `spring config without a rendering override uses the default renderer`() {
    val config = object : SpringSinglePageApplicationConfig,
      SinglePageApplicationConfig by TestSinglePageApplicationConfig(routes = listOf(RouteManifest("/test", "Index"))) {
    }

    mockMvc(config).get("/test").andExpect {
      status { isOk() }
      content { string(org.hamcrest.Matchers.containsString("<div id=\"root\"></div>")) }
    }
  }

  private fun mockMvc(
    config: SinglePageApplicationConfig,
    properties: RoutingProperties = RoutingProperties()
  ): MockMvc {
    return MockMvcBuilders.routerFunctions(
      SpringRouterFunctionFactory(
        routeConfigs = listOf(config),
        routeResponseService = RouteResponseService(
          configs = listOf(config),
          invalidPathParameterStatus = properties.server.invalidPathParameterStatus,
          invalidQueryStringStatus = properties.server.invalidQueryStringStatus
        ),
        requestFactory = DefaultRouteRequestFactory(),
        htmlRenderer = DefaultHtmlRenderer(properties)
      ).routes()
    ).build()
  }
}
