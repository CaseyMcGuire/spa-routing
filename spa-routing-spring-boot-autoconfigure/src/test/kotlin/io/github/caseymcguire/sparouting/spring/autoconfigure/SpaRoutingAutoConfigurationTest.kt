package io.github.caseymcguire.sparouting.spring.autoconfigure

import com.caseymcguiredotcom.sparoutecontract.route
import io.github.caseymcguire.sparouting.spring.config.SinglePageApplicationConfig
import io.github.caseymcguire.sparouting.spring.config.SinglePageApplicationRouteRegistry
import io.github.caseymcguire.sparouting.spring.rendering.DefaultSpaHtmlRenderer
import io.github.caseymcguire.sparouting.spring.rendering.SpaHtmlRenderer
import io.github.caseymcguire.sparouting.spring.request.DefaultSpaRouteRequestFactory
import io.github.caseymcguire.sparouting.spring.request.SpaRouteRequest
import io.github.caseymcguire.sparouting.spring.request.SpaRouteRequestFactory
import io.github.caseymcguire.sparouting.spring.response.SpaRouteResponseService
import io.github.caseymcguire.sparouting.spring.rules.SpaRouteResponseEvaluator
import io.github.caseymcguire.sparouting.spring.rules.SpaRouteRuleActionResolver
import io.github.caseymcguire.sparouting.spring.testsupport.TestSinglePageApplicationConfig
import io.github.caseymcguire.sparouting.spring.testsupport.TestSpaApplicationDefinition
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.function.RouterFunction
import org.springframework.web.servlet.function.ServerResponse

class SpaRoutingAutoConfigurationTest {
  private val contextRunner = WebApplicationContextRunner()
    .withConfiguration(AutoConfigurations.of(SpaRoutingAutoConfiguration::class.java))
    .withUserConfiguration(TestRouteConfiguration::class.java)

  @Test
  fun `default beans are created when spring mvc is on the classpath`() {
    contextRunner.run { context ->
      assertThat(context).hasSingleBean(SinglePageApplicationRouteRegistry::class.java)
      assertThat(context).hasSingleBean(SpaRouteRuleActionResolver::class.java)
      assertThat(context).hasSingleBean(SpaRouteResponseEvaluator::class.java)
      assertThat(context).hasSingleBean(DefaultSpaRouteRequestFactory::class.java)
      assertThat(context).hasSingleBean(DefaultSpaHtmlRenderer::class.java)
      assertThat(context).hasSingleBean(SpaRouteResponseService::class.java)
      assertThat(context).hasSingleBean(RouterFunction::class.java)
    }
  }

  @Test
  fun `user defined html renderer wins over default`() {
    contextRunner
      .withUserConfiguration(CustomHtmlRendererConfiguration::class.java)
      .run { context ->
        assertThat(context).hasSingleBean(SpaHtmlRenderer::class.java)
        assertThat(context).getBean(SpaHtmlRenderer::class.java)
          .isInstanceOf(CustomSpaHtmlRenderer::class.java)
      }
  }

  @Test
  fun `user defined request factory wins over default`() {
    contextRunner
      .withUserConfiguration(CustomRequestFactoryConfiguration::class.java)
      .run { context ->
        assertThat(context).hasSingleBean(SpaRouteRequestFactory::class.java)
        assertThat(context).getBean(SpaRouteRequestFactory::class.java)
          .isInstanceOf(CustomSpaRouteRequestFactory::class.java)
      }
  }

  @Test
  fun `server disabled property disables router function`() {
    contextRunner
      .withPropertyValues("spa-routing.server.enabled=false")
      .run { context ->
        assertThat(context).doesNotHaveBean(RouterFunction::class.java)
        assertThat(context).hasSingleBean(SpaRouteResponseService::class.java)
      }
  }

  @Test
  fun `properties bind correctly`() {
    contextRunner
      .withPropertyValues(
        "spa-routing.server.invalid-path-parameter-status=422",
        "spa-routing.assets.bundle-base-path=/assets",
        "spa-routing.assets.include-route-stylesheet=false",
        "spa-routing.assets.global-stylesheet=/assets/global.css"
      )
      .run { context ->
        val properties = context.getBean(SpaRoutingProperties::class.java)
        assertThat(properties.server.invalidPathParameterStatus).isEqualTo(422)
        assertThat(properties.assets.bundleBasePath).isEqualTo("/assets")
        assertThat(properties.assets.includeRouteStylesheet).isFalse()
        assertThat(properties.assets.globalStylesheet).isEqualTo("/assets/global.css")
      }
  }

  @Configuration(proxyBeanMethods = false)
  class TestRouteConfiguration {
    @Bean
    fun testSpaApplicationConfig(): SinglePageApplicationConfig {
      return TestSinglePageApplicationConfig(
        TestSpaApplicationDefinition(routes = listOf(route("home", "Home")))
      )
    }
  }

  @Configuration(proxyBeanMethods = false)
  class CustomHtmlRendererConfiguration {
    @Bean
    fun customSpaHtmlRenderer(): SpaHtmlRenderer {
      return CustomSpaHtmlRenderer()
    }
  }

  @Configuration(proxyBeanMethods = false)
  class CustomRequestFactoryConfiguration {
    @Bean
    fun customSpaRouteRequestFactory(): SpaRouteRequestFactory {
      return CustomSpaRouteRequestFactory()
    }
  }

  class CustomSpaHtmlRenderer : SpaHtmlRenderer {
    override fun render(application: SinglePageApplicationConfig): ServerResponse {
      return ServerResponse.ok().body("custom")
    }
  }

  class CustomSpaRouteRequestFactory : SpaRouteRequestFactory {
    override fun create(
      serverRequest: org.springframework.web.servlet.function.ServerRequest,
      application: SinglePageApplicationConfig,
      route: com.caseymcguiredotcom.sparoutecontract.SpaRouteDefinition
    ): SpaRouteRequest {
      return SpaRouteRequest(application.applicationId, route.id, serverRequest.method().name(), serverRequest.path())
    }
  }
}
