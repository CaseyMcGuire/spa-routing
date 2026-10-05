package com.sparouting.spring.autoconfigure

import com.sparouting.contract.RouteManifest
import com.sparouting.contract.ApplicationAccessHandler
import com.sparouting.contract.SinglePageApplicationConfig
import com.sparouting.contract.RouteRequest
import com.sparouting.contract.parameter
import com.sparouting.runtime.response.RouteResponseService
import com.sparouting.spring.rendering.DefaultHtmlRenderer
import com.sparouting.spring.rendering.HtmlRenderer
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
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.servlet.function.RouterFunction
import org.springframework.web.servlet.function.ServerResponse

class RoutingAutoConfigurationTest {
  private val contextRunner = WebApplicationContextRunner()
    .withConfiguration(AutoConfigurations.of(RoutingAutoConfiguration::class.java))
    .withUserConfiguration(TestRouteConfiguration::class.java)

  @Test
  fun `default HTTP beans are created without exposing runtime internals`() {
    contextRunner.run { context ->
      assertThat(context).hasSingleBean(ApplicationAccessHandler::class.java)
      assertThat(context.getBean(SinglePageApplicationConfig::class.java).applicationAccessHandler)
        .isSameAs(context.getBean(ApplicationAccessHandler::class.java))
      assertThat(context).doesNotHaveBean("singlePageApplicationRouteRegistry")
      assertThat(context).doesNotHaveBean("routeAccessEvaluator")
      assertThat(context).hasSingleBean(DefaultRouteRequestFactory::class.java)
      assertThat(context).hasSingleBean(DefaultHtmlRenderer::class.java)
      assertThat(context).doesNotHaveBean(RouteResponseService::class.java)
      assertThat(context.getBeansOfType(RouterFunction::class.java)).hasSize(2)
      assertThat(context).hasBean("routerFunction")
      assertThat(context).hasBean("routeDecisionRouterFunction")
    }
  }

  @Test
  fun `an application service bean does not replace built-in route evaluation`() {
    contextRunner
      .withBean(RouteResponseService::class.java, Supplier { RouteResponseService(emptyList()) })
      .run { context ->
        assertThat(context.getBean(RouteResponseService::class.java).evaluate(RouteRequest("test", "Home")).statusCode)
          .isEqualTo(404)
        val mockMvc = MockMvcBuilders.routerFunctions(
          *context.getBeansOfType(RouterFunction::class.java).values.toTypedArray()
        ).build()
        mockMvc.get("/test/home").andExpect { status { isOk() } }
        mockMvc.get("/__spa/route-decision?applicationId=test&routeId=Home").andExpect {
          status { isOk() }
          jsonPath("$.statusCode") { value(200) }
        }
      }
  }

  @Test
  fun `user defined html renderer wins over default`() {
    contextRunner
      .withUserConfiguration(CustomHtmlRendererConfiguration::class.java)
      .run { context ->
        assertThat(context).hasSingleBean(HtmlRenderer::class.java)
        assertThat(context).getBean(HtmlRenderer::class.java)
          .isInstanceOf(CustomHtmlRenderer::class.java)
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
  fun `server disabled property disables router function`() {
    contextRunner
      .withPropertyValues("spa-routing.server.enabled=false")
      .run { context ->
        assertThat(context).doesNotHaveBean("routerFunction")
        assertThat(context).hasBean("routeDecisionRouterFunction")
        assertThat(context).doesNotHaveBean(RouteResponseService::class.java)
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
  fun `properties configure rendering and validation for both endpoints`() {
    contextRunner
      .withPropertyValues(
        "spa-routing.server.invalid-path-parameter-status=422",
        "spa-routing.server.invalid-query-string-status=409",
        "spa-routing.route-decision.path=/internal/spa-route-decision",
        "spa-routing.assets.bundle-base-path=/assets",
        "spa-routing.assets.include-route-stylesheet=false",
        "spa-routing.assets.global-stylesheet=/assets/global.css"
      )
      .run { context ->
        val properties = context.getBean(RoutingProperties::class.java)
        assertThat(properties.server.invalidPathParameterStatus).isEqualTo(422)
        assertThat(properties.server.invalidQueryStringStatus).isEqualTo(409)
        assertThat(properties.routeDecision.path).isEqualTo("/internal/spa-route-decision")
        assertThat(properties.assets.bundleBasePath).isEqualTo("/assets")
        assertThat(properties.assets.includeRouteStylesheet).isFalse()
        assertThat(properties.assets.globalStylesheet).isEqualTo("/assets/global.css")

        val mockMvc = MockMvcBuilders.routerFunctions(
          *context.getBeansOfType(RouterFunction::class.java).values.toTypedArray()
        ).build()
        mockMvc.get("/test/users/42?q=one&q=two").andExpect { status { isEqualTo(409) } }
        mockMvc.get("/internal/spa-route-decision") {
          param("applicationId", "test")
          param("routeId", "User")
          param("parameters.id", "42")
          param("queryString.q", "one", "two")
        }.andExpect {
          status { isOk() }
          jsonPath("$.statusCode") { value(409) }
        }
        mockMvc.get("/internal/spa-route-decision?applicationId=test&routeId=User").andExpect {
          status { isOk() }
          jsonPath("$.statusCode") { value(422) }
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
  class CustomHtmlRendererConfiguration {
    @Bean
    fun customHtmlRenderer(): HtmlRenderer {
      return CustomHtmlRenderer()
    }
  }

  @Configuration(proxyBeanMethods = false)
  class CustomRequestFactoryConfiguration {
    @Bean
    fun customRouteRequestFactory(): RouteRequestFactory {
      return CustomRouteRequestFactory()
    }
  }

  class CustomHtmlRenderer : HtmlRenderer {
    override fun render(application: SinglePageApplicationConfig): ServerResponse {
      return ServerResponse.ok().body("custom")
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
