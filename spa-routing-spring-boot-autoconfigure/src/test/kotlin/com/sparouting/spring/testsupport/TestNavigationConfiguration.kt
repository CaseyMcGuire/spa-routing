package com.sparouting.spring.testsupport

import com.sparouting.contract.SinglePageApplicationConfig
import com.sparouting.runtime.evaluation.RouteFailureHandler
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
internal class TestNavigationConfiguration {
  @Bean
  fun failureApplication(): SinglePageApplicationConfig = failureConfig

  @Bean
  @ConditionalOnMissingBean(RouteFailureHandler::class)
  fun defaultFailureHandler(): RouteFailureHandler = testFailureHandler
}
