package io.github.caseymcguire.sparouting.spring.web

import com.sparouting.contract.route
import com.sparouting.contract.string
import io.github.caseymcguire.sparouting.spring.autoconfigure.SpaRoutingProperties
import io.github.caseymcguire.sparouting.spring.config.SinglePageApplicationConfig
import io.github.caseymcguire.sparouting.spring.rendering.DefaultSpaHtmlRenderer
import io.github.caseymcguire.sparouting.spring.request.DefaultSpaRouteRequestFactory
import io.github.caseymcguire.sparouting.spring.rules.SpaRouteResponseEvaluator
import io.github.caseymcguire.sparouting.spring.rules.SpaRouteRuleAction
import io.github.caseymcguire.sparouting.spring.rules.SpaRouteRuleActionResolver
import io.github.caseymcguire.sparouting.spring.rules.SpaRouteRuleResult
import io.github.caseymcguire.sparouting.spring.testsupport.RecordingRule
import io.github.caseymcguire.sparouting.spring.testsupport.TestSinglePageApplicationConfig
import io.github.caseymcguire.sparouting.spring.testsupport.TestSpaApplicationDefinition
import io.github.caseymcguire.sparouting.spring.testsupport.TestSpaRouteKey
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class SpaRouterFunctionFactoryTest {
  @Test
  fun `known route returns html`() {
    val mockMvc = mockMvc(
      TestSinglePageApplicationConfig(
        application = TestSpaApplicationDefinition(routes = listOf(route("users/{id}", "UserDetail", listOf(string("id"))))),
        rules = listOf(RecordingRule(SpaRouteRuleResult.Allow))
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
        application = TestSpaApplicationDefinition(routes = listOf(route("users/{id}", "UserDetail", listOf(string("id"))))),
        rules = listOf(RecordingRule(SpaRouteRuleResult.Allow))
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
        application = TestSpaApplicationDefinition(routes = listOf(route("admin", "Admin"))),
        rules = listOf(RecordingRule(SpaRouteRuleResult.Deny(SpaRouteRuleAction.redirect("/login"))))
      )
    )

    mockMvc.get("/test/admin")
      .andExpect {
        status { isFound() }
        header { string("Location", "/login") }
      }
  }

  @Test
  fun `app and route level rules are both applied`() {
    val mockMvc = mockMvc(
      TestSinglePageApplicationConfig(
        application = TestSpaApplicationDefinition(routes = listOf(route("settings", "Settings"))),
        rules = listOf(RecordingRule(SpaRouteRuleResult.Allow)),
        routeRules = mapOf(
          TestSpaRouteKey("test", "Settings") to listOf(
            RecordingRule(SpaRouteRuleResult.Deny(SpaRouteRuleAction.notFound()))
          )
        )
      )
    )

    mockMvc.get("/test/settings")
      .andExpect {
        status { isNotFound() }
      }
  }

  private fun mockMvc(
    config: SinglePageApplicationConfig,
    properties: SpaRoutingProperties = SpaRoutingProperties()
  ) = MockMvcBuilders.routerFunctions(
    SpaRouterFunctionFactory(
      routeConfigs = listOf(config),
      routeResponseEvaluator = SpaRouteResponseEvaluator(SpaRouteRuleActionResolver(listOf(config))),
      requestFactory = DefaultSpaRouteRequestFactory(),
      htmlRenderer = DefaultSpaHtmlRenderer(properties),
      properties = properties
    ).routes()
  ).build()
}
