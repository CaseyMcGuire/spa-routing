package com.sparouting.spring.access

import com.sparouting.contract.AccessDecision
import com.sparouting.contract.Route
import com.sparouting.contract.RouteAccessContext
import com.sparouting.contract.RouteAccessHandler
import com.sparouting.contract.RouteTarget
import com.sparouting.contract.parameter
import com.sparouting.contract.RouteManifest
import com.sparouting.runtime.access.ApplicationAccessHandler
import com.sparouting.runtime.config.SinglePageApplicationConfig
import com.sparouting.runtime.request.RouteRequest
import com.sparouting.spring.autoconfigure.RoutingAutoConfiguration
import com.sparouting.spring.testsupport.applicationAccessHandler
import com.sparouting.spring.testsupport.TestSinglePageApplicationConfig
import com.sparouting.spring.testsupport.TestSinglePageApplicationManifest
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
  fun `missing application handler fails startup even with both endpoints disabled`() {
    runner(config(enabled = false), emptyList())
      .withPropertyValues("spa-routing.server.enabled=false", "spa-routing.route-decision.enabled=false")
      .run { context ->
        assertThat(context).hasFailed()
        assertThat(context.startupFailure).hasStackTraceContaining("Missing application access handler for test")
      }
  }

  @Test
  fun `duplicate application handler beans fail startup`() {
    val config = config(enabled = false)
    runner(config, listOf(applicationAccessHandler(config.manifest), applicationAccessHandler(config.manifest)))
      .run { context ->
        assertThat(context).hasFailed()
        assertThat(context.startupFailure).hasStackTraceContaining("Expected exactly one application access handler for test, found 2")
      }
  }

  @Test
  fun `application handler bean for an unknown application fails startup`() {
    val config = config(enabled = false)
    val unknown = TestSinglePageApplicationManifest(id = "unknown", routes = emptyList())
    runner(config, listOf(applicationAccessHandler(config.manifest), applicationAccessHandler(unknown)))
      .run { context ->
        assertThat(context).hasFailed()
        assertThat(context.startupFailure).hasStackTraceContaining("Application access handler registered for unknown application: unknown")
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
        assertThat(context.startupFailure).hasStackTraceContaining("does not declare hasAccessHandler = true")
      }
  }

  @Test
  fun `handler for an unknown route fails startup`() {
    val config = TestSinglePageApplicationConfig(
      manifest = TestSinglePageApplicationManifest(routes = listOf(RouteManifest("/test/known", "Known")))
    )
    runner(config)
      .withBean(PostAccessHandler::class.java, Supplier { PostAccessHandler() })
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
    val applicationHandler = applicationAccessHandler(config.manifest) { request ->
      applicationRequests.add(request)
      AccessDecision.Allow
    }
    runner(config, listOf(applicationHandler)).withBean(PostAccessHandler::class.java, Supplier { handler }).run { context ->
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
        jsonPath("$.statusCode") { value(200) }
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
        jsonPath("$.statusCode") { value(302) }
        jsonPath("$.location") { value("/test/missing?from=post+access") }
      }
      assertThat(handler.requests).hasSize(4)
      assertThat(applicationRequests).hasSize(4)

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
      assertThat(applicationRequests).hasSize(4)

      // Unflagged routes still work without an access handler.
      mockMvc.get("/test/missing").andExpect { status { isOk() } }
      assertThat(handler.requests).hasSize(4)
    }
  }

  @Test
  fun `application redirect prevents route checks for page and navigation requests`() {
    val handler = PostAccessHandler()
    val config = config()
    val applicationHandler = applicationAccessHandler(config.manifest) { AccessDecision.Redirect(RouteTarget("test", "Missing")) }
    runner(config, listOf(applicationHandler))
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

  private fun runner(
    config: SinglePageApplicationConfig = config(),
    applicationHandlers: List<ApplicationAccessHandler> = listOf(applicationAccessHandler(config.manifest))
  ): WebApplicationContextRunner {
    var runner = WebApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(RoutingAutoConfiguration::class.java))
      .withBean(SinglePageApplicationConfig::class.java, Supplier { config })
    applicationHandlers.forEachIndexed { index, handler ->
      runner = runner.withBean("applicationAccess$index", ApplicationAccessHandler::class.java, Supplier { handler })
    }
    return runner
  }

  private fun config(enabled: Boolean = true): TestSinglePageApplicationConfig {
    return TestSinglePageApplicationConfig(
      manifest = TestSinglePageApplicationManifest(routes = listOf(
        RouteManifest("/test/posts/{id}", "Post", queryString = listOf(parameter("view").optional()), hasAccessHandler = enabled),
        RouteManifest("/test/missing", "Missing", queryString = listOf(parameter("from").optional()))
      ))
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
