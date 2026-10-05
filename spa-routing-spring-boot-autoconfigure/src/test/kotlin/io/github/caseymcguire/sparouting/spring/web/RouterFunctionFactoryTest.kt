package io.github.caseymcguire.sparouting.spring.web

import com.sparouting.contract.route
import io.github.caseymcguire.sparouting.runtime.access.RouteAccessEvaluator
import io.github.caseymcguire.sparouting.runtime.config.SinglePageApplicationConfig
import io.github.caseymcguire.sparouting.runtime.config.SinglePageApplicationRouteRegistry
import io.github.caseymcguire.sparouting.runtime.response.RouteResponseService
import io.github.caseymcguire.sparouting.runtime.rules.RouteRuleAction
import io.github.caseymcguire.sparouting.runtime.rules.RouteRuleActionResolver
import io.github.caseymcguire.sparouting.runtime.rules.RouteRuleResult
import io.github.caseymcguire.sparouting.spring.autoconfigure.RoutingProperties
import io.github.caseymcguire.sparouting.spring.config.SpringSinglePageApplicationConfig
import io.github.caseymcguire.sparouting.spring.rendering.DefaultHtmlRenderer
import io.github.caseymcguire.sparouting.spring.request.DefaultRouteRequestFactory
import io.github.caseymcguire.sparouting.spring.testsupport.RecordingRule
import io.github.caseymcguire.sparouting.spring.testsupport.TestSinglePageApplicationConfig
import io.github.caseymcguire.sparouting.spring.testsupport.TestSinglePageApplicationDefinition
import kotlin.test.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.servlet.function.ServerResponse

class RouterFunctionFactoryTest {
  @Test
  fun `known route returns html`() {
    val mockMvc = mockMvc(
      TestSinglePageApplicationConfig(
        application = TestSinglePageApplicationDefinition(routes = listOf(route("users/{id}", "UserDetail"))),
        rules = listOf(RecordingRule(RouteRuleResult.Allow))
      )
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
      TestSinglePageApplicationConfig(
        application = TestSinglePageApplicationDefinition(routes = listOf(route("users/{id}", "UserDetail"))),
        rules = listOf(RecordingRule(RouteRuleResult.Allow))
      )
    )

    mockMvc.get("/test/users")
      .andExpect {
        status { isNotFound() }
      }
  }

  @Test
  fun `denying rule returns response instead of html`() {
    val mockMvc = mockMvc(
      TestSinglePageApplicationConfig(
        application = TestSinglePageApplicationDefinition(routes = listOf(route("admin", "Admin"))),
        rules = listOf(RecordingRule(RouteRuleResult.Deny(RouteRuleAction.redirect("/login"))))
      )
    )

    mockMvc.get("/test/admin")
      .andExpect {
        status { isFound() }
        header { string("Location", "/login") }
      }
  }

  @Test
  fun `application without an allowing rule does not serve html`() {
    val mockMvc = mockMvc(
      TestSinglePageApplicationConfig(
        application = TestSinglePageApplicationDefinition(routes = listOf(route("settings", "Settings")))
      )
    )

    mockMvc.get("/test/settings")
      .andExpect {
        status { isNotFound() }
      }
  }

  @Test
  fun `spring config can render an application-specific response after access is allowed`() {
    val config = object : SpringSinglePageApplicationConfig {
      override val application = TestSinglePageApplicationDefinition(routes = listOf(route("", "Index")))
      override val rules = listOf(RecordingRule(RouteRuleResult.Allow))

      override fun renderHtml(): ServerResponse = ServerResponse.ok().body("Custom HTML")
    }

    mockMvc(config).get("/test").andExpect {
      status { isOk() }
      content { string("Custom HTML") }
    }
  }

  @Test
  fun `application-specific rendering is skipped when access is denied`() {
    var rendered = false
    val config = object : SpringSinglePageApplicationConfig {
      override val application = TestSinglePageApplicationDefinition(routes = listOf(route("", "Index")))

      override fun renderHtml(): ServerResponse {
        rendered = true
        return ServerResponse.ok().body("Custom HTML")
      }
    }

    mockMvc(config).get("/test").andExpect { status { isNotFound() } }
    assertFalse(rendered)
  }

  @Test
  fun `spring config without a rendering override uses the default renderer`() {
    val config = object : SpringSinglePageApplicationConfig {
      override val application = TestSinglePageApplicationDefinition(routes = listOf(route("", "Index")))
      override val rules = listOf(RecordingRule(RouteRuleResult.Allow))
    }

    mockMvc(config).get("/test").andExpect {
      status { isOk() }
      content { string(org.hamcrest.Matchers.containsString("<div id=\"root\"></div>")) }
    }
  }

  private fun mockMvc(
    config: SinglePageApplicationConfig,
    properties: RoutingProperties = RoutingProperties()
  ) = MockMvcBuilders.routerFunctions(
    RouterFunctionFactory(
      routeConfigs = listOf(config),
      routeResponseService = RouteResponseService(
        routeRegistry = SinglePageApplicationRouteRegistry(listOf(config)),
        accessEvaluator = RouteAccessEvaluator(SinglePageApplicationRouteRegistry(listOf(config))),
        actionResolver = RouteRuleActionResolver(listOf(config)),
        invalidPathParameterStatus = properties.server.invalidPathParameterStatus,
        invalidQueryStringStatus = properties.server.invalidQueryStringStatus
      ),
      requestFactory = DefaultRouteRequestFactory(),
      htmlRenderer = DefaultHtmlRenderer(properties)
    ).routes()
  ).build()
}
