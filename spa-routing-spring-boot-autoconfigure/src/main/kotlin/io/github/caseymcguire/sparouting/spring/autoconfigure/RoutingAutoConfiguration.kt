package io.github.caseymcguire.sparouting.spring.autoconfigure

import com.sparouting.contract.RouteAccessHandler
import io.github.caseymcguire.sparouting.spring.access.RouteHandlerRegistry
import io.github.caseymcguire.sparouting.spring.config.SinglePageApplicationConfig
import io.github.caseymcguire.sparouting.spring.config.SinglePageApplicationRouteRegistry
import io.github.caseymcguire.sparouting.spring.rendering.DefaultHtmlRenderer
import io.github.caseymcguire.sparouting.spring.rendering.HtmlRenderer
import io.github.caseymcguire.sparouting.spring.request.DefaultRouteRequestFactory
import io.github.caseymcguire.sparouting.spring.request.RouteRequestFactory
import io.github.caseymcguire.sparouting.spring.response.RouteResponseService
import io.github.caseymcguire.sparouting.spring.rules.RouteResponseEvaluator
import io.github.caseymcguire.sparouting.spring.rules.RouteRuleActionResolver
import io.github.caseymcguire.sparouting.spring.web.RouteDecisionRouterFunctionFactory
import io.github.caseymcguire.sparouting.spring.web.RouterFunctionFactory
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
@EnableConfigurationProperties(RoutingProperties::class)
class RoutingAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean
  fun singlePageApplicationRouteRegistry(
    configs: List<SinglePageApplicationConfig>
  ): SinglePageApplicationRouteRegistry {
    return SinglePageApplicationRouteRegistry(configs)
  }

  @Bean
  @ConditionalOnMissingBean
  fun routeRuleActionResolver(
    configs: List<SinglePageApplicationConfig>
  ): RouteRuleActionResolver {
    return RouteRuleActionResolver(configs)
  }

  @Bean
  @ConditionalOnMissingBean
  fun routeHandlerRegistry(
    routeRegistry: SinglePageApplicationRouteRegistry,
    handlers: List<RouteAccessHandler<*>>
  ): RouteHandlerRegistry {
    return RouteHandlerRegistry(routeRegistry, handlers)
  }

  @Bean
  @ConditionalOnMissingBean
  fun routeResponseEvaluator(
    actionResolver: RouteRuleActionResolver,
    handlerRegistry: RouteHandlerRegistry
  ): RouteResponseEvaluator {
    return RouteResponseEvaluator(actionResolver, handlerRegistry)
  }

  @Bean
  @ConditionalOnMissingBean
  fun routeRequestFactory(): RouteRequestFactory {
    return DefaultRouteRequestFactory()
  }

  @Bean
  @ConditionalOnMissingBean
  fun htmlRenderer(
    properties: RoutingProperties
  ): HtmlRenderer {
    return DefaultHtmlRenderer(properties)
  }

  @Bean
  @ConditionalOnMissingBean
  fun routeResponseService(
    routeRegistry: SinglePageApplicationRouteRegistry,
    evaluator: RouteResponseEvaluator,
    properties: RoutingProperties
  ): RouteResponseService {
    return RouteResponseService(
      routeRegistry = routeRegistry,
      evaluator = evaluator,
      invalidPathParameterStatus = properties.server.invalidPathParameterStatus,
      invalidQueryStringStatus = properties.server.invalidQueryStringStatus
    )
  }

  @Bean
  @ConditionalOnMissingBean(name = ["routeDecisionRouterFunction"])
  @ConditionalOnProperty(
    prefix = "spa-routing.route-decision",
    name = ["enabled"],
    matchIfMissing = true
  )
  fun routeDecisionRouterFunction(
    responseService: RouteResponseService,
    properties: RoutingProperties
  ): RouterFunction<ServerResponse> {
    return RouteDecisionRouterFunctionFactory(
      responseService = responseService,
      properties = properties
    ).routes()
  }

  @Bean
  @ConditionalOnProperty(
    prefix = "spa-routing.server",
    name = ["enabled"],
    matchIfMissing = true
  )
  fun routerFunction(
    configs: List<SinglePageApplicationConfig>,
    evaluator: RouteResponseEvaluator,
    requestFactory: RouteRequestFactory,
    htmlRenderer: HtmlRenderer,
    properties: RoutingProperties
  ): RouterFunction<ServerResponse> {
    return RouterFunctionFactory(
      routeConfigs = configs,
      routeResponseEvaluator = evaluator,
      requestFactory = requestFactory,
      htmlRenderer = htmlRenderer,
      properties = properties
    ).routes()
  }
}
