package io.github.caseymcguire.sparouting.spring.access

import com.sparouting.contract.AccessDecision
import com.sparouting.contract.Route
import com.sparouting.contract.RouteAccessContext
import com.sparouting.contract.RouteAccessHandler
import com.sparouting.contract.RouteTarget
import com.sparouting.contract.parameter
import com.sparouting.contract.route
import io.github.caseymcguire.sparouting.runtime.config.SinglePageApplicationConfig
import io.github.caseymcguire.sparouting.spring.autoconfigure.RoutingAutoConfiguration
import io.github.caseymcguire.sparouting.spring.testsupport.RecordingApplicationAccessHandler
import io.github.caseymcguire.sparouting.spring.testsupport.TestSinglePageApplicationConfig
import io.github.caseymcguire.sparouting.spring.testsupport.TestSinglePageApplicationDefinition
import java.util.function.Supplier
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.servlet.function.RouterFunction

class RouteAccessTest {
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
  fun `handler for an unknown route fails startup`() {
    val config = TestSinglePageApplicationConfig(
      application = TestSinglePageApplicationDefinition(routes = listOf(route("known", "Known")))
    )
    runner(config)
      .withBean(PostAccessHandler::class.java, Supplier { PostAccessHandler() })
      .run { context ->
        assertThat(context).hasFailed()
        assertThat(context.startupFailure).hasStackTraceContaining("Access handler registered for test:Post")
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
  fun `application redirect prevents route checks for page and navigation requests`() {
    val handler = PostAccessHandler()
    val applicationHandler = RecordingApplicationAccessHandler(AccessDecision.Redirect(RouteTarget("test", "Missing")))
    runner(config().copy(accessHandler = applicationHandler))
      .withBean(PostAccessHandler::class.java, Supplier { handler })
      .run { context ->
        val mockMvc = MockMvcBuilders.routerFunctions(
          *context.getBeansOfType(RouterFunction::class.java).values.toTypedArray()
        ).build()

        mockMvc.get("/test/posts/42").andExpect {
          status { isFound() }
          header { string("Location", "/test/missing") }
        }
        mockMvc.get("/__spa/route-decision") {
          param("applicationId", "test")
          param("routeId", "Post")
          param("parameters.id", "42")
        }.andExpect {
          jsonPath("$.statusCode") { value(302) }
          jsonPath("$.location") { value("/test/missing") }
        }
        assertThat(handler.requests).isEmpty()
      }
  }

  private fun runner(config: SinglePageApplicationConfig = config()): WebApplicationContextRunner {
    return WebApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(RoutingAutoConfiguration::class.java))
      .withBean(SinglePageApplicationConfig::class.java, Supplier { config })
  }

  private fun config(enabled: Boolean = true): TestSinglePageApplicationConfig {
    return TestSinglePageApplicationConfig(
      application = TestSinglePageApplicationDefinition(routes = listOf(
        route("posts/{id}", "Post", queryString = listOf(parameter("view").optional()), generateAccessHandler = enabled),
        route("missing", "Missing", queryString = listOf(parameter("from").optional()))
      )),
      accessHandler = RecordingApplicationAccessHandler(AccessDecision.Allow)
    )
  }

  data class PostRequest(val id: String, val context: RouteAccessContext)

  class PostAccessHandler : RouteAccessHandler<PostRequest>(Route("test", "Post")) {
    val requests = mutableListOf<PostRequest>()

    override fun createRequest(context: RouteAccessContext): PostRequest {
      return PostRequest(id = context.pathParameters.getValue("id"), context = context)
    }

    override fun evaluate(request: PostRequest): AccessDecision {
      requests.add(request)
      if (request.id == "42") {
        return AccessDecision.Allow
      }
      return AccessDecision.Redirect(RouteTarget(
        applicationId = "test",
        routeId = "Missing",
        queryString = mapOf("from" to listOf("post access"))
      ))
    }
  }
}
