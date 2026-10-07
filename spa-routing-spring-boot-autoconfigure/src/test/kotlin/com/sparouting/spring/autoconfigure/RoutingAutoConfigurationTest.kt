package com.sparouting.spring.autoconfigure

import com.sparouting.runtime.evaluation.RouteRequestEvaluator
import com.sparouting.runtime.evaluation.RouteFailureHandler
import com.sparouting.contract.AccessDecision
import com.sparouting.contract.RouteTarget
import com.sparouting.spring.testsupport.unknownRouteResult
import com.sparouting.spring.testsupport.testFailureHandler
import com.sparouting.spring.testsupport.TestNavigationConfiguration
import com.sparouting.spring.testsupport.testEvaluator
import com.sparouting.contract.RouteManifest
import com.sparouting.contract.ApplicationAccessHandler
import com.sparouting.contract.HtmlRenderer
import com.sparouting.contract.SinglePageApplicationConfig
import com.sparouting.contract.RouteRequest
import com.sparouting.contract.parameter
import com.sparouting.runtime.evaluation.RouteResult
import com.sparouting.runtime.response.DefaultRouteHttpResponseConverter
import com.sparouting.runtime.response.RouteHttpResponse
import com.sparouting.runtime.response.RouteHttpResponseConverter
import com.sparouting.spring.request.DefaultRouteRequestFactory
import com.sparouting.spring.request.RouteRequestFactory
import com.sparouting.spring.testsupport.TestSinglePageApplicationConfig
import com.sparouting.spring.testsupport.applicationAccessHandler
import java.util.function.Supplier
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.servlet.function.RouterFunction

class RoutingAutoConfigurationTest {
  private val contextRunner = WebApplicationContextRunner()
    .withConfiguration(AutoConfigurations.of(RoutingAutoConfiguration::class.java))
    .withUserConfiguration(TestNavigationConfiguration::class.java)
    .withUserConfiguration(TestRouteConfiguration::class.java)

  @Test
  fun `default HTTP beans are created without exposing runtime internals`() {
    contextRunner.run { context ->
      assertThat(context).hasSingleBean(ApplicationAccessHandler::class.java)
      assertThat(context.getBeansOfType(SinglePageApplicationConfig::class.java).values.single { it.id == "test" }.applicationAccessHandler)
        .isSameAs(context.getBean(ApplicationAccessHandler::class.java))
      assertThat(context).doesNotHaveBean("singlePageApplicationRouteRegistry")
      assertThat(context).doesNotHaveBean("routeAccessEvaluator")
      assertThat(context).hasSingleBean(DefaultRouteRequestFactory::class.java)
      assertThat(context).hasSingleBean(DefaultRouteHttpResponseConverter::class.java)
      assertThat(context).doesNotHaveBean(HtmlRenderer::class.java)
      assertThat(context).doesNotHaveBean(RouteRequestEvaluator::class.java)
      assertThat(context.getBeansOfType(RouterFunction::class.java)).hasSize(2)
      assertThat(context).hasBean("routerFunction")
      assertThat(context).hasBean("routeDecisionRouterFunction")
    }
  }

  @Test
  fun `an application evaluator bean does not replace built-in route evaluation`() {
    contextRunner
      .withBean(RouteRequestEvaluator::class.java, Supplier { testEvaluator(emptyList()) })
      .run { context ->
        assertThat(context.getBean(RouteRequestEvaluator::class.java).evaluate(RouteRequest("test", "Home")))
          .isEqualTo(unknownRouteResult)
        val mockMvc = MockMvcBuilders.routerFunctions(
          *context.getBeansOfType(RouterFunction::class.java).values.toTypedArray()
        ).build()
        mockMvc.get("/test/home").andExpect { status { isOk() } }
        mockMvc.get("/__spa/route-decision?applicationId=test&routeId=Home").andExpect {
          status { isOk() }
          jsonPath("$.type") { value("allowed") }
        }
      }
  }

  @Test
  fun `each application renders with its own configured renderer only on page loads`() {
    val rendered = mutableListOf<String>()
    val configs = listOf("one", "two").map { id ->
      TestSinglePageApplicationConfig(
        id = id,
        name = "Application $id",
        routes = listOf(RouteManifest("/$id", "Home")),
        htmlRenderer = HtmlRenderer { application ->
          rendered.add(application.id)
          "<h1>$id: ${application.name}</h1>"
        }
      )
    }
    WebApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(RoutingAutoConfiguration::class.java))
    .withUserConfiguration(TestNavigationConfiguration::class.java)
      .withBean("firstConfig", SinglePageApplicationConfig::class.java, Supplier { configs[0] })
      .withBean("secondConfig", SinglePageApplicationConfig::class.java, Supplier { configs[1] })
      .run { context ->
        val mockMvc = MockMvcBuilders.routerFunctions(
          *context.getBeansOfType(RouterFunction::class.java).values.toTypedArray()
        ).build()
        configs.forEach { config ->
          mockMvc.get("/${config.id}").andExpect {
            status { isOk() }
            content {
              contentTypeCompatibleWith(MediaType.TEXT_HTML)
              string("<h1>${config.id}: ${config.name}</h1>")
            }
          }
          mockMvc.get("/__spa/route-decision?applicationId=${config.id}&routeId=Home").andExpect {
            status { isOk() }
            jsonPath("$.type") { value("allowed") }
          }
        }
        assertThat(rendered).containsExactly("one", "two")
      }
  }

  @Test
  fun `user defined request factory wins over default`() {
    contextRunner
      .withUserConfiguration(CustomRequestFactoryConfiguration::class.java)
      .run { context ->
        assertThat(context).hasSingleBean(RouteRequestFactory::class.java)
        assertThat(context).getBean(RouteRequestFactory::class.java)
          .isInstanceOf(CustomRouteRequestFactory::class.java)
      }
  }

  @Test
  fun `custom failure handler supplies the same recovery outcome for pages and navigation`() {
    val requests = mutableListOf<RouteRequest>()
    val handler = object : RouteFailureHandler {
      override fun unknownRoute(request: RouteRequest): AccessDecision.Denied = testFailureHandler.unknownRoute(request)
      override fun invalidRequest(request: RouteRequest): AccessDecision.Denied {
        requests.add(request)
        return AccessDecision.Denied(destination = RouteTarget("test", "Home"))
      }
    }
    contextRunner.withBean(RouteFailureHandler::class.java, Supplier { handler }).run { context ->
      assertThat(context).hasSingleBean(RouteFailureHandler::class.java)
      val mockMvc = MockMvcBuilders.routerFunctions(
        *context.getBeansOfType(RouterFunction::class.java).values.toTypedArray()
      ).build()
      mockMvc.get("/test/users/42") {
        param("q", "one", "two")
        header("X-User", "reader")
      }.andExpect {
        status { isFound() }
        header { string("Location", "/test/home") }
        content { string("") }
      }
      mockMvc.get("/__spa/route-decision") {
        param("applicationId", "test")
        param("routeId", "User")
        param("parameters.id", "42")
        param("queryString.q", "one", "two")
        header("X-User", "reader")
      }.andExpect {
        status { isOk() }
        header {
          string("Cache-Control", "no-store")
          doesNotExist("Location")
        }
        jsonPath("$.type") { value("invalid_request") }
        jsonPath("$.destination") { value("/test/home") }
        jsonPath("$.reason") { doesNotExist() }
        jsonPath("$.statusCode") { doesNotExist() }
        jsonPath("$.location") { doesNotExist() }
      }
      assertThat(requests).hasSize(2)
      assertThat(requests[0]).isEqualTo(requests[1])
      assertThat(requests[0]).isEqualTo(RouteRequest(
        applicationId = "test",
        routeId = "User",
        pathParameters = mapOf("id" to "42"),
        queryString = mapOf("q" to listOf("one", "two")),
        headers = mapOf("X-User" to listOf("reader"))
      ))
      mockMvc.get("/__spa/route-decision?applicationId=missing&routeId=Home").andExpect {
        status { isOk() }
        jsonPath("$.type") { value("unknown_route") }
        jsonPath("$.destination") { value("/errors/not-found") }
        jsonPath("$.reason") { doesNotExist() }
      }
    }
  }

  @Test
  fun `custom responses suppress HTML when an allowed route is mapped to a redirect or error`() {
    for (response in listOf(RouteHttpResponse(303, "/elsewhere"), RouteHttpResponse(403))) {
      val config = TestSinglePageApplicationConfig(
        routes = listOf(RouteManifest("/test", "Home")),
        htmlRenderer = HtmlRenderer { error("Overridden responses must not render HTML") }
      )
      WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(RoutingAutoConfiguration::class.java))
    .withUserConfiguration(TestNavigationConfiguration::class.java)
        .withBean(SinglePageApplicationConfig::class.java, Supplier { config })
        .withBean(RouteHttpResponseConverter::class.java, Supplier {
          RouteHttpResponseConverter { _, result ->
            assertThat(result).isEqualTo(RouteResult.Allowed)
            response
          }
        })
        .run { context ->
          val mockMvc = MockMvcBuilders.routerFunctions(
            *context.getBeansOfType(RouterFunction::class.java).values.toTypedArray()
          ).build()
          mockMvc.get("/__spa/route-decision?applicationId=test&routeId=Home").andExpect {
            status { isOk() }
            jsonPath("$.type") { value("allowed") }
            jsonPath("$.statusCode") { doesNotExist() }
            jsonPath("$.destination") { doesNotExist() }
          }
          mockMvc.get("/test").andExpect {
            status { isEqualTo(response.statusCode) }
            content { string("") }
            header {
              val location = response.location
              if (location == null) {
                doesNotExist("Location")
              } else {
                string("Location", location)
              }
            }
          }
        }
    }
  }

  @Test
  fun `server disabled property disables router function`() {
    contextRunner
      .withPropertyValues("spa-routing.server.enabled=false")
      .run { context ->
        assertThat(context).doesNotHaveBean("routerFunction")
        assertThat(context).hasBean("routeDecisionRouterFunction")
        assertThat(context).doesNotHaveBean(RouteRequestEvaluator::class.java)
      }
  }

  @Test
  fun `route decision disabled property disables decision router function`() {
    contextRunner
      .withPropertyValues("spa-routing.route-decision.enabled=false")
      .run { context ->
        assertThat(context).hasBean("routerFunction")
        assertThat(context).doesNotHaveBean("routeDecisionRouterFunction")
      }
  }

  @Test
  fun `configured decision endpoint returns recovery destinations for path and query failures`() {
    contextRunner.withPropertyValues("spa-routing.route-decision.path=/internal/spa-route-decision").run { context ->
      val mockMvc = MockMvcBuilders.routerFunctions(
        *context.getBeansOfType(RouterFunction::class.java).values.toTypedArray()
      ).build()
      mockMvc.get("/test/users/42?q=one&q=two").andExpect {
        status { isFound() }
        header { string("Location", "/errors/invalid-request") }
      }
      for (query in listOf("", "&parameters.id=42&queryString.q=one&queryString.q=two")) {
        mockMvc.get("/internal/spa-route-decision?applicationId=test&routeId=User$query").andExpect {
          status { isOk() }
          jsonPath("$.type") { value("invalid_request") }
          jsonPath("$.destination") { value("/errors/invalid-request") }
          jsonPath("$.reason") { doesNotExist() }
        }
      }
    }
  }

  @Configuration(proxyBeanMethods = false)
  class TestRouteConfiguration {
    @Bean
    internal fun testApplicationAccessHandler(): ApplicationAccessHandler<TestSinglePageApplicationConfig> = applicationAccessHandler()

    @Bean
    internal fun testApplicationConfig(
      applicationAccessHandler: ApplicationAccessHandler<TestSinglePageApplicationConfig>
    ): SinglePageApplicationConfig = TestSinglePageApplicationConfig(
      routes = listOf(
        RouteManifest("/test/home", "Home"),
        RouteManifest("/test/users/{id}", "User", queryString = listOf(parameter("q").optional()))
      ),
      applicationAccessHandler = applicationAccessHandler
    )
  }

  @Configuration(proxyBeanMethods = false)
  class CustomRequestFactoryConfiguration {
    @Bean
    fun customRouteRequestFactory(): RouteRequestFactory {
      return CustomRouteRequestFactory()
    }
  }

  class CustomRouteRequestFactory : RouteRequestFactory {
    override fun create(
      serverRequest: org.springframework.web.servlet.function.ServerRequest,
      application: SinglePageApplicationConfig,
      route: com.sparouting.contract.RouteManifest
    ): RouteRequest {
      return RouteRequest(application.id, route.id)
    }
  }
}
