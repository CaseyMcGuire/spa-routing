package io.github.caseymcguire.sparouting.spring.autoconfigure

import com.sparouting.contract.AccessDecision
import com.sparouting.contract.route
import io.github.caseymcguire.sparouting.runtime.access.ApplicationAccessHandler
import io.github.caseymcguire.sparouting.runtime.access.RouteAccessEvaluator
import io.github.caseymcguire.sparouting.runtime.config.SinglePageApplicationConfig
import io.github.caseymcguire.sparouting.runtime.config.SinglePageApplicationRouteRegistry
import io.github.caseymcguire.sparouting.runtime.request.RouteRequest
import io.github.caseymcguire.sparouting.runtime.response.RouteResponseService
import io.github.caseymcguire.sparouting.spring.rendering.DefaultHtmlRenderer
import io.github.caseymcguire.sparouting.spring.rendering.HtmlRenderer
import io.github.caseymcguire.sparouting.spring.request.DefaultRouteRequestFactory
import io.github.caseymcguire.sparouting.spring.request.RouteRequestFactory
import io.github.caseymcguire.sparouting.spring.testsupport.TestSinglePageApplicationConfig
import io.github.caseymcguire.sparouting.spring.testsupport.TestSinglePageApplicationDefinition
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.function.RouterFunction
import org.springframework.web.servlet.function.ServerResponse

class RoutingAutoConfigurationTest {
  private val contextRunner = WebApplicationContextRunner()
    .withConfiguration(AutoConfigurations.of(RoutingAutoConfiguration::class.java))
    .withUserConfiguration(TestRouteConfiguration::class.java)

  @Test
  fun `default beans are created when spring mvc is on the classpath`() {
    contextRunner.run { context ->
      assertThat(context).hasSingleBean(SinglePageApplicationRouteRegistry::class.java)
      assertThat(context).hasSingleBean(RouteAccessEvaluator::class.java)
      assertThat(context).hasSingleBean(ApplicationAccessHandler::class.java)
      assertThat(context.getBean(SinglePageApplicationConfig::class.java).accessHandler)
        .isSameAs(context.getBean(ApplicationAccessHandler::class.java))
      assertThat(context).doesNotHaveBean("routeRuleActionResolver")
      assertThat(context).hasBean("routeAccessEvaluator")
      assertThat(context).doesNotHaveBean("routeResponseEvaluator")
      assertThat(context).doesNotHaveBean("routeHandlerRegistry")
      assertThat(context).hasSingleBean(DefaultRouteRequestFactory::class.java)
      assertThat(context).hasSingleBean(DefaultHtmlRenderer::class.java)
      assertThat(context).hasSingleBean(RouteResponseService::class.java)
      assertThat(context.getBeansOfType(RouterFunction::class.java)).hasSize(2)
      assertThat(context).hasBean("routerFunction")
      assertThat(context).hasBean("routeDecisionRouterFunction")
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
        assertThat(context).hasSingleBean(RouteResponseService::class.java)
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
  fun `properties bind correctly`() {
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
      }
  }

  @Configuration(proxyBeanMethods = false)
  class TestRouteConfiguration {
    @Bean
    fun applicationAccessHandler(): ApplicationAccessHandler = ApplicationAccessHandler { AccessDecision.Allow }

    @Bean
    fun testApplicationConfig(applicationAccessHandler: ApplicationAccessHandler): SinglePageApplicationConfig {
      return TestSinglePageApplicationConfig(
        application = TestSinglePageApplicationDefinition(routes = listOf(route("home", "Home"))),
        accessHandler = applicationAccessHandler
      )
    }
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
      route: com.sparouting.contract.RouteDefinition
    ): RouteRequest {
      return RouteRequest(application.applicationId, route.id, serverRequest.method().name(), serverRequest.path())
    }
  }
}
