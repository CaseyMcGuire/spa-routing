package com.sparouting.spring.web

import com.sparouting.contract.AccessDecision
import com.sparouting.contract.HtmlRenderer
import com.sparouting.contract.RouteTarget
import com.sparouting.contract.RouteManifest
import com.sparouting.contract.SinglePageApplicationConfig
import com.sparouting.runtime.evaluation.RouteRequestEvaluator
import com.sparouting.spring.autoconfigure.RoutingProperties
import com.sparouting.spring.request.DefaultRouteRequestFactory
import com.sparouting.spring.testsupport.applicationAccessHandler
import com.sparouting.spring.testsupport.TestSinglePageApplicationConfig
import kotlin.test.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders

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
  fun `config renderer receives application metadata after access is allowed`() {
    val config = TestSinglePageApplicationConfig(
      name = "Custom app",
      routes = listOf(RouteManifest("/test", "Index")),
      htmlRenderer = HtmlRenderer { application -> "<h1>${application.name}</h1>" }
    )

    mockMvc(config).get("/test").andExpect {
      status { isOk() }
      content {
        contentTypeCompatibleWith(MediaType.TEXT_HTML)
        string("<h1>Custom app</h1>")
      }
    }
  }

  @Test
  fun `application-specific rendering is skipped when the application redirects`() {
    var rendered = false
    val config = TestSinglePageApplicationConfig(
      routes = listOf(RouteManifest("/test", "Index"), RouteManifest("/test/login", "Login")),
      applicationAccessHandler = applicationAccessHandler { AccessDecision.Redirect(RouteTarget("test", "Login")) },
      htmlRenderer = HtmlRenderer {
        rendered = true
        "Custom HTML"
      }
    )

    mockMvc(config)
      .get("/test").andExpect { status { isFound() } }
    assertFalse(rendered)
  }

  @Test
  fun `invalid requests do not render HTML`() {
    val config = TestSinglePageApplicationConfig(
      routes = listOf(RouteManifest("/test", "Index", queryString = listOf(com.sparouting.contract.parameter("q")))),
      htmlRenderer = HtmlRenderer { error("Rendering must follow validation") }
    )

    for (statusCode in listOf(400, 200)) {
      val properties = RoutingProperties().apply { server.invalidQueryStringStatus = statusCode }
      mockMvc(config, properties).get("/test").andExpect {
        status { isEqualTo(statusCode) }
        content { string("") }
      }
    }
  }

  private fun mockMvc(
    config: SinglePageApplicationConfig,
    properties: RoutingProperties = RoutingProperties()
  ): MockMvc {
    return MockMvcBuilders.routerFunctions(
      SpringRouterFunctionFactory(
        routeConfigs = listOf(config),
        evaluator = RouteRequestEvaluator(listOf(config)),
        requestFactory = DefaultRouteRequestFactory(),
        properties = properties
      ).routes()
    ).build()
  }
}
