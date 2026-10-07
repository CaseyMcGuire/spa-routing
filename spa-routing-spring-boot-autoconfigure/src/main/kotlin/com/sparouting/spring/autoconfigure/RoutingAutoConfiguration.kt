package com.sparouting.spring.autoconfigure

import com.sparouting.contract.SinglePageApplicationConfig
import com.sparouting.runtime.evaluation.RouteRequestEvaluator
import com.sparouting.runtime.response.DefaultRouteHttpResponseConverter
import com.sparouting.runtime.response.RouteHttpResponseConverter
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
class RoutingAutoConfiguration(
  private val configs: List<SinglePageApplicationConfig>,
  private val properties: RoutingProperties
) {
  private val evaluator = RouteRequestEvaluator(configs)

  @Bean
  @ConditionalOnMissingBean
  fun routeRequestFactory(): RouteRequestFactory {
    return DefaultRouteRequestFactory()
  }

  @Bean
  @ConditionalOnMissingBean
  fun routeHttpResponseConverter(): RouteHttpResponseConverter {
    return DefaultRouteHttpResponseConverter(
      invalidRequestStatus = properties.server.invalidRequestStatus
    )
  }

  @Bean
  @ConditionalOnMissingBean(name = ["routeDecisionRouterFunction"])
  @ConditionalOnProperty(
    prefix = "spa-routing.route-decision",
    name = ["enabled"],
    matchIfMissing = true
  )
  fun routeDecisionRouterFunction(responseConverter: RouteHttpResponseConverter): RouterFunction<ServerResponse> {
    return RouteDecisionRouterFunctionFactory(
      evaluator = evaluator,
      properties = properties,
      responseConverter = responseConverter
    ).routes()
  }

  @Bean
  @ConditionalOnProperty(
    prefix = "spa-routing.server",
    name = ["enabled"],
    matchIfMissing = true
  )
  fun routerFunction(
    requestFactory: RouteRequestFactory,
    responseConverter: RouteHttpResponseConverter
  ): RouterFunction<ServerResponse> {
    return SpringRouterFunctionFactory(
      routeConfigs = configs,
      evaluator = evaluator,
      requestFactory = requestFactory,
      responseConverter = responseConverter
    ).routes()
  }
}
