package io.github.caseymcguire.sparouting.spring.autoconfigure

import io.github.caseymcguire.sparouting.spring.config.SinglePageApplicationConfig
import io.github.caseymcguire.sparouting.spring.config.SinglePageApplicationRouteRegistry
import io.github.caseymcguire.sparouting.spring.rendering.DefaultSpaHtmlRenderer
import io.github.caseymcguire.sparouting.spring.rendering.SpaHtmlRenderer
import io.github.caseymcguire.sparouting.spring.request.DefaultSpaRouteRequestFactory
import io.github.caseymcguire.sparouting.spring.request.SpaRouteRequestFactory
import io.github.caseymcguire.sparouting.spring.response.SpaRouteResponseService
import io.github.caseymcguire.sparouting.spring.rules.SpaRouteResponseEvaluator
import io.github.caseymcguire.sparouting.spring.rules.SpaRouteRuleActionResolver
import io.github.caseymcguire.sparouting.spring.web.SpaRouterFunctionFactory
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.web.servlet.function.RouterFunction
import org.springframework.web.servlet.function.ServerRequest
import org.springframework.web.servlet.function.ServerResponse

@AutoConfiguration
@ConditionalOnClass(RouterFunction::class, ServerRequest::class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(SpaRoutingProperties::class)
class SpaRoutingAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean
  fun singlePageApplicationRouteRegistry(
    configs: List<SinglePageApplicationConfig>
  ): SinglePageApplicationRouteRegistry {
    return SinglePageApplicationRouteRegistry(configs)
  }

  @Bean
  @ConditionalOnMissingBean
  fun spaRouteRuleActionResolver(
    configs: List<SinglePageApplicationConfig>
  ): SpaRouteRuleActionResolver {
    return SpaRouteRuleActionResolver(configs)
  }

  @Bean
  @ConditionalOnMissingBean
  fun spaRouteResponseEvaluator(
    actionResolver: SpaRouteRuleActionResolver
  ): SpaRouteResponseEvaluator {
    return SpaRouteResponseEvaluator(actionResolver)
  }

  @Bean
  @ConditionalOnMissingBean
  fun spaRouteRequestFactory(): SpaRouteRequestFactory {
    return DefaultSpaRouteRequestFactory()
  }

  @Bean
  @ConditionalOnMissingBean
  fun spaHtmlRenderer(
    properties: SpaRoutingProperties
  ): SpaHtmlRenderer {
    return DefaultSpaHtmlRenderer(properties)
  }

  @Bean
  @ConditionalOnMissingBean
  fun spaRouteResponseService(
    routeRegistry: SinglePageApplicationRouteRegistry,
    evaluator: SpaRouteResponseEvaluator
  ): SpaRouteResponseService {
    return SpaRouteResponseService(routeRegistry, evaluator)
  }

  @Bean
  @ConditionalOnProperty(
    prefix = "spa-routing.server",
    name = ["enabled"],
    matchIfMissing = true
  )
  fun spaRouterFunction(
    configs: List<SinglePageApplicationConfig>,
    evaluator: SpaRouteResponseEvaluator,
    requestFactory: SpaRouteRequestFactory,
    htmlRenderer: SpaHtmlRenderer,
    properties: SpaRoutingProperties
  ): RouterFunction<ServerResponse> {
    return SpaRouterFunctionFactory(
      routeConfigs = configs,
      routeResponseEvaluator = evaluator,
      requestFactory = requestFactory,
      htmlRenderer = htmlRenderer,
      properties = properties
    ).routes()
  }
}
