package com.sparouting.spring.access

import com.sparouting.spring.testsupport.TestNavigationConfiguration
import com.sparouting.contract.AccessDecision
import com.sparouting.contract.DenialReason
import com.sparouting.contract.Route
import com.sparouting.contract.RouteAccessContext
import com.sparouting.contract.RouteAccessHandler
import com.sparouting.contract.RouteTarget
import com.sparouting.contract.parameter
import com.sparouting.contract.RouteManifest
import com.sparouting.contract.ApplicationAccessHandler
import com.sparouting.contract.SinglePageApplicationConfig
import com.sparouting.contract.RouteRequest
import com.sparouting.spring.autoconfigure.RoutingAutoConfiguration
import com.sparouting.spring.testsupport.routeAccessHandlers
import com.sparouting.spring.testsupport.applicationAccessHandler
import com.sparouting.spring.testsupport.TestSinglePageApplicationConfig
import java.util.function.Supplier
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.servlet.function.RouterFunction

private val applicationDenialReason = DenialReason(code = "application_access_required", message = "You cannot view this app.")
private val postDenialReason = DenialReason(code = "post_not_found", message = "That post could not be found.")

class RouteAccessTest {
  @Test
  fun `standalone handler beans are not discovered implicitly`() {
    runner(config(enabled = false))
      .withBean(ApplicationAccessHandler::class.java, Supplier { applicationAccessHandler { error("Unused handler") } })
      .withBean(PostAccessHandler::class.java, Supplier { PostAccessHandler() })
      .run { context ->
        assertThat(context).hasNotFailed()
        val mockMvc = MockMvcBuilders.routerFunctions(
          *context.getBeansOfType(RouterFunction::class.java).values.toTypedArray()
        ).build()
        mockMvc.get("/test/posts/42").andExpect { status { isOk() } }
        assertThat(context.getBean(PostAccessHandler::class.java).requests).isEmpty()
      }
  }

  @Test
  fun `missing handler fails startup even when page serving is disabled`() {
    runner().withPropertyValues("spa-routing.server.enabled=false").run { context ->
      assertThat(context).hasFailed()
      assertThat(context.startupFailure).hasStackTraceContaining("Missing access handler for test:Post")
    }
  }

  @Test
  fun `duplicate handlers fail startup`() {
    runner(config().copy(routeAccessHandlers = routeAccessHandlers(PostAccessHandler(), PostAccessHandler())))
      .run { context ->
        assertThat(context).hasFailed()
        assertThat(context.startupFailure).hasStackTraceContaining("Expected exactly one access handler for test:Post, found 2")
      }
  }

  @Test
  fun `handler for a route without the flag fails startup`() {
    runner(config(enabled = false).copy(routeAccessHandlers = routeAccessHandlers(PostAccessHandler())))
      .run { context ->
        assertThat(context).hasFailed()
        assertThat(context.startupFailure).hasStackTraceContaining("does not declare hasAccessHandler = true")
      }
  }

  @Test
  fun `handler for an unknown route fails startup`() {
    val config = TestSinglePageApplicationConfig(routes = listOf(RouteManifest("/test/known", "Known")))
    runner(config.copy(routeAccessHandlers = routeAccessHandlers(PostAccessHandler())))
      .run { context ->
        assertThat(context).hasFailed()
        assertThat(context.startupFailure).hasStackTraceContaining("Access handler registered for test:Post")
      }
  }

  @Test
  fun `page and navigation decisions pass the same request data to both handlers after validation`() {
    val handler = PostAccessHandler()
    val config = config()
    val applicationRequests = mutableListOf<RouteRequest>()
    val applicationHandler = applicationAccessHandler { request ->
      applicationRequests.add(request)
      AccessDecision.Allowed
    }
    runner(config.copy(
      applicationAccessHandler = applicationHandler,
      routeAccessHandlers = routeAccessHandlers(handler)
    )).run { context ->
      assertThat(context).hasNotFailed()
      val mockMvc = MockMvcBuilders.routerFunctions(
        *context.getBeansOfType(RouterFunction::class.java).values.toTypedArray()
      ).build()

      mockMvc.get("/test/posts/42") {
        param("view", "reader")
        header("X-User", "casey")
        header("X-Permission", "read", "edit")
      }.andExpect { status { isOk() } }
      mockMvc.get("/__spa/route-decision") {
        param("applicationId", "test")
        param("routeId", "Post")
        param("parameters.id", "42")
        param("queryString.view", "reader")
        header("X-User", "casey")
        header("X-Permission", "read", "edit")
      }.andExpect {
        status { isOk() }
        jsonPath("$.type") { value("allowed") }
      }
      assertThat(applicationRequests).hasSize(2)
      assertThat(applicationRequests[0]).isEqualTo(applicationRequests[1])
      assertThat(applicationRequests[0].applicationId).isEqualTo("test")
      assertThat(applicationRequests[0].routeId).isEqualTo("Post")
      assertThat(applicationRequests[0].pathParameters).isEqualTo(mapOf("id" to "42"))
      assertThat(applicationRequests[0].queryString).isEqualTo(mapOf("view" to listOf("reader")))
      assertThat(applicationRequests[0].header("x-user")).containsExactly("casey")
      assertThat(applicationRequests[0].header("x-permission")).containsExactly("read", "edit")
      assertThat(handler.requests).hasSize(2)
      assertThat(handler.requests[0]).isEqualTo(handler.requests[1])
      handler.requests.forEach { request ->
        assertThat(request.id).isEqualTo("42")
        assertThat(request.context.queryString["view"]).containsExactly("reader")
        assertThat(request.context.header("x-user")).containsExactly("casey")
        assertThat(request.context.header("x-permission")).containsExactly("read", "edit")
        assertThat(request.context.method).isEqualTo("GET")
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
        jsonPath("$.type") { value("denied") }
        jsonPath("$.destination") { value("/test/missing?from=post+access") }
        jsonPath("$.reason.code") { value(postDenialReason.code) }
        jsonPath("$.reason.message") { value(postDenialReason.message) }
      }
      assertThat(handler.requests).hasSize(4)
      assertThat(applicationRequests).hasSize(4)

      mockMvc.get("/test/posts/42") {
        param("view", "one", "two")
      }.andExpect { status { isFound() } }
      mockMvc.get("/__spa/route-decision") {
        param("applicationId", "test")
        param("routeId", "Post")
        param("parameters.id", "42")
        param("queryString.view", "one", "two")
      }.andExpect { jsonPath("$.type") { value("invalid_request") } }
      mockMvc.get("/__spa/route-decision") {
        param("applicationId", "test")
        param("routeId", "Post")
      }.andExpect { jsonPath("$.type") { value("invalid_request") } }
      assertThat(handler.requests).hasSize(4)
      assertThat(applicationRequests).hasSize(4)

      // Unflagged routes still work without an access handler.
      mockMvc.get("/test/missing").andExpect { status { isOk() } }
      assertThat(handler.requests).hasSize(4)
    }
  }

  @Test
  fun `application denial prevents route checks for page and navigation requests`() {
    val handler = PostAccessHandler()
    val config = config()
    val applicationHandler = applicationAccessHandler {
      AccessDecision.Denied(reason = applicationDenialReason, destination = RouteTarget("test", "Missing"))
    }
    runner(config.copy(
      applicationAccessHandler = applicationHandler,
      routeAccessHandlers = routeAccessHandlers(handler)
    ))
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
          jsonPath("$.type") { value("denied") }
          jsonPath("$.destination") { value("/test/missing") }
          jsonPath("$.reason.code") { value(applicationDenialReason.code) }
          jsonPath("$.reason.message") { value(applicationDenialReason.message) }
        }
        assertThat(handler.requests).isEmpty()
      }
  }

  private fun runner(config: SinglePageApplicationConfig = config()): WebApplicationContextRunner {
    return WebApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(RoutingAutoConfiguration::class.java))
    .withUserConfiguration(TestNavigationConfiguration::class.java)
      .withBean(SinglePageApplicationConfig::class.java, Supplier { config })
  }

  private fun config(enabled: Boolean = true): TestSinglePageApplicationConfig {
    return TestSinglePageApplicationConfig(routes = listOf(
      RouteManifest("/test/posts/{id}", "Post", queryString = listOf(parameter("view").optional()), hasAccessHandler = enabled),
      RouteManifest("/test/missing", "Missing", queryString = listOf(parameter("from").optional()))
    ))
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
        return AccessDecision.Allowed
      }
      return AccessDecision.Denied(
        reason = postDenialReason,
        destination = RouteTarget(
          applicationId = "test",
          routeId = "Missing",
          queryString = mapOf("from" to listOf("post access"))
        )
      )
    }
  }
}
