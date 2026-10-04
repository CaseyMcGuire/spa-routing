package io.github.caseymcguire.sparouting.spring.web

import com.sparouting.contract.route
import io.github.caseymcguire.sparouting.spring.autoconfigure.RoutingProperties
import io.github.caseymcguire.sparouting.spring.config.SinglePageApplicationConfig
import io.github.caseymcguire.sparouting.spring.rendering.DefaultHtmlRenderer
import io.github.caseymcguire.sparouting.spring.request.DefaultRouteRequestFactory
import io.github.caseymcguire.sparouting.spring.rules.RouteResponseEvaluator
import io.github.caseymcguire.sparouting.spring.rules.RouteRuleAction
import io.github.caseymcguire.sparouting.spring.rules.RouteRuleActionResolver
import io.github.caseymcguire.sparouting.spring.rules.RouteRuleResult
import io.github.caseymcguire.sparouting.spring.testsupport.RecordingRule
import io.github.caseymcguire.sparouting.spring.testsupport.TestSinglePageApplicationConfig
import io.github.caseymcguire.sparouting.spring.testsupport.TestSinglePageApplicationDefinition
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders

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

  private fun mockMvc(
    config: SinglePageApplicationConfig,
    properties: RoutingProperties = RoutingProperties()
  ) = MockMvcBuilders.routerFunctions(
    RouterFunctionFactory(
      routeConfigs = listOf(config),
      routeResponseEvaluator = RouteResponseEvaluator(RouteRuleActionResolver(listOf(config))),
      requestFactory = DefaultRouteRequestFactory(),
      htmlRenderer = DefaultHtmlRenderer(properties),
      properties = properties
    ).routes()
  ).build()
}
