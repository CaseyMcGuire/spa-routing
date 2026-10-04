package io.github.caseymcguire.sparouting.spring.access

import com.sparouting.contract.RouteDecision
import com.sparouting.contract.RouteAccessHandler
import com.sparouting.contract.RouteAccessContext
import com.sparouting.contract.RouteTarget
import com.sparouting.contract.parameter
import com.sparouting.contract.route
import io.github.caseymcguire.sparouting.spring.autoconfigure.SpaRoutingAutoConfiguration
import io.github.caseymcguire.sparouting.spring.config.SinglePageApplicationConfig
import io.github.caseymcguire.sparouting.spring.response.SpaRouteResponseRequest
import io.github.caseymcguire.sparouting.spring.response.SpaRouteResponseService
import io.github.caseymcguire.sparouting.spring.rules.SpaRouteRuleAction
import io.github.caseymcguire.sparouting.spring.rules.SpaRouteRuleResult
import io.github.caseymcguire.sparouting.spring.testsupport.RecordingRule
import io.github.caseymcguire.sparouting.spring.testsupport.TestSinglePageApplicationConfig
import io.github.caseymcguire.sparouting.spring.testsupport.TestSinglePageApplicationDefinition
import com.sparouting.contract.Route
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.servlet.function.RouterFunction
import java.util.function.Supplier

class SpaRouteAccessTest {
  private val postKey = Route("test", "Post")

  @Test
  fun `missing handler fails startup even when page serving is disabled`() {
    runner().withPropertyValues("spa-routing.server.enabled=false").run { context ->
      assertThat(context).hasFailed()
      assertThat(context.startupFailure).hasStackTraceContaining("Missing access handler for test:Post")
    }
  }

  @Test
  fun `duplicate handlers fail startup`() {
    runner()
      .withBean("firstAccess", PostAccessHandler::class.java, Supplier { PostAccessHandler() })
      .withBean("secondAccess", PostAccessHandler::class.java, Supplier { PostAccessHandler() })
      .run { context ->
        assertThat(context).hasFailed()
        assertThat(context.startupFailure).hasStackTraceContaining("Expected exactly one access handler for test:Post, found 2")
      }
  }

  @Test
  fun `handler for a route without the flag fails startup`() {
    runner(config(enabled = false))
      .withBean(PostAccessHandler::class.java, Supplier { PostAccessHandler() })
      .run { context ->
        assertThat(context).hasFailed()
        assertThat(context.startupFailure).hasStackTraceContaining("does not declare generateAccessHandler = true")
      }
  }

  @Test
  fun `page and navigation decisions invoke the same injected handler after validation`() {
    val handler = PostAccessHandler()
    runner().withBean(PostAccessHandler::class.java, Supplier { handler }).run { context ->
      assertThat(context).hasNotFailed()
      val mockMvc = MockMvcBuilders.routerFunctions(
        *context.getBeansOfType(RouterFunction::class.java).values.toTypedArray()
      ).build()

      mockMvc.get("/test/posts/42") {
        param("view", "reader")
        header("X-User", "casey")
      }.andExpect { status { isOk() } }
      mockMvc.get("/__spa/route-decision") {
        param("applicationId", "test")
        param("routeId", "Post")
        param("parameters.id", "42")
        param("queryString.view", "reader")
        header("X-User", "casey")
      }.andExpect {
        status { isOk() }
        jsonPath("$.statusCode") { value(200) }
      }
      assertThat(handler.requests).hasSize(2)
      handler.requests.forEach { request ->
        assertThat(request.id).isEqualTo("42")
        assertThat(request.context.queryString["view"]).containsExactly("reader")
        assertThat(request.context.header("x-user")).containsExactly("casey")
        assertThat(request.context.path).isEqualTo("/test/posts/42")
      }

      mockMvc.get("/test/posts/missing").andExpect {
        status { isFound() }
        header { string("Location", "/test/missing?from=post+access") }
      }
      mockMvc.get("/__spa/route-decision") {
        param("applicationId", "test")
        param("routeId", "Post")
        param("parameters.id", "missing")
      }.andExpect {
        status { isOk() }
        jsonPath("$.statusCode") { value(302) }
        jsonPath("$.location") { value("/test/missing?from=post+access") }
      }
      assertThat(handler.requests).hasSize(4)

      mockMvc.get("/test/posts/42") {
        param("view", "one", "two")
      }.andExpect { status { isBadRequest() } }
      mockMvc.get("/__spa/route-decision") {
        param("applicationId", "test")
        param("routeId", "Post")
        param("parameters.id", "42")
        param("queryString.view", "one", "two")
      }.andExpect { jsonPath("$.statusCode") { value(400) } }
      mockMvc.get("/__spa/route-decision") {
        param("applicationId", "test")
        param("routeId", "Post")
      }.andExpect { jsonPath("$.statusCode") { value(400) } }
      assertThat(handler.requests).hasSize(4)

      // Unflagged routes still work without an access handler.
      mockMvc.get("/test/missing").andExpect { status { isOk() } }
      assertThat(handler.requests).hasSize(4)
    }
  }

  @Test
  fun `application denial prevents handler evaluation`() {
    val handler = PostAccessHandler()
    runner(config().copy(rules = emptyList()))
      .withBean(PostAccessHandler::class.java, Supplier { handler })
      .run { context ->
        val result = context.getBean(SpaRouteResponseService::class.java).evaluate(request("42"))
        assertThat(result.statusCode).isEqualTo(404)
        assertThat(handler.requests).isEmpty()
      }
  }

  @Test
  fun `access allow continues to legacy vetoes and legacy allow cannot bypass access`() {
    for (rule in listOf(SpaRouteRuleResult.Allow, SpaRouteRuleResult.Deny(SpaRouteRuleAction.status(451)))) {
      val handler = PostAccessHandler()
      runner(config().copy(routeRules = mapOf(postKey to listOf(RecordingRule(rule)))))
        .withBean(PostAccessHandler::class.java, Supplier { handler })
        .run { context ->
          val service = context.getBean(SpaRouteResponseService::class.java)
          assertThat(service.evaluate(request("missing")).statusCode).isEqualTo(302)
          assertThat(service.evaluate(request("42")).statusCode)
            .isEqualTo(if (rule is SpaRouteRuleResult.Deny) 451 else 200)
        }
    }
  }

  private fun runner(config: SinglePageApplicationConfig = config()): WebApplicationContextRunner {
    return WebApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(SpaRoutingAutoConfiguration::class.java))
      .withBean(SinglePageApplicationConfig::class.java, Supplier { config })
  }

  private fun config(enabled: Boolean = true): TestSinglePageApplicationConfig {
    return TestSinglePageApplicationConfig(
      application = TestSinglePageApplicationDefinition(routes = listOf(
        route("posts/{id}", "Post", queryString = listOf(parameter("view").optional()), generateAccessHandler = enabled),
        route("missing", "Missing", queryString = listOf(parameter("from").optional()))
      )),
      rules = listOf(RecordingRule(SpaRouteRuleResult.Allow))
    )
  }

  private fun request(id: String): SpaRouteResponseRequest {
    return SpaRouteResponseRequest(applicationId = "test", routeId = "Post", parameters = mapOf("id" to id))
  }

  data class PostRequest(val id: String, val context: RouteAccessContext)

  class PostAccessHandler : RouteAccessHandler<PostRequest>(Route("test", "Post")) {
    val requests = mutableListOf<PostRequest>()

    override fun createRequest(context: RouteAccessContext): PostRequest {
      return PostRequest(id = context.pathParameters.getValue("id"), context = context)
    }

    override fun evaluate(request: PostRequest): RouteDecision {
      requests.add(request)
      if (request.id == "42") {
        return RouteDecision.Allow
      }
      return RouteDecision.Redirect(RouteTarget(
        applicationId = "test",
        routeId = "Missing",
        queryString = mapOf("from" to listOf("post access"))
      ))
    }
  }
}
