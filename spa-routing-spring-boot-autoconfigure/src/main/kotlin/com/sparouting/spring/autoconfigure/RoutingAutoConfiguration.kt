package com.sparouting.spring.autoconfigure

import com.sparouting.contract.RouteAccessHandler
import com.sparouting.runtime.access.ApplicationAccessHandler
import com.sparouting.runtime.access.RouteAccessEvaluator
import com.sparouting.runtime.config.SinglePageApplicationConfig
import com.sparouting.runtime.config.SinglePageApplicationRouteRegistry
import com.sparouting.runtime.response.RouteResponseService
import com.sparouting.spring.rendering.DefaultHtmlRenderer
import com.sparouting.spring.rendering.HtmlRenderer
import com.sparouting.spring.request.DefaultRouteRequestFactory
import com.sparouting.spring.request.RouteRequestFactory
import com.sparouting.spring.web.RouteDecisionRouterFunctionFactory
import com.sparouting.spring.web.SpringRouterFunctionFactory
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
    configs: List<SinglePageApplicationConfig>,
    applicationHandlers: List<ApplicationAccessHandler>,
    routeHandlers: List<RouteAccessHandler<*>>
  ): SinglePageApplicationRouteRegistry {
    return SinglePageApplicationRouteRegistry(configs, applicationHandlers, routeHandlers)
  }

  @Bean
  @ConditionalOnMissingBean
  fun routeAccessEvaluator(
    routeRegistry: SinglePageApplicationRouteRegistry
  ): RouteAccessEvaluator {
    return RouteAccessEvaluator(routeRegistry)
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
    accessEvaluator: RouteAccessEvaluator,
    properties: RoutingProperties
  ): RouteResponseService {
    return RouteResponseService(
      routeRegistry = routeRegistry,
      accessEvaluator = accessEvaluator,
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
    responseService: RouteResponseService,
    requestFactory: RouteRequestFactory,
    htmlRenderer: HtmlRenderer
  ): RouterFunction<ServerResponse> {
    return SpringRouterFunctionFactory(
      routeConfigs = configs,
      routeResponseService = responseService,
      requestFactory = requestFactory,
      htmlRenderer = htmlRenderer
    ).routes()
  }
}
