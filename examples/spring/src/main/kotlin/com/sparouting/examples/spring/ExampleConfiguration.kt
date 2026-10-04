package com.sparouting.examples.spring

import com.sparouting.contract.applications.BlogApplication
import io.github.caseymcguire.sparouting.runtime.config.SinglePageApplicationConfig
import io.github.caseymcguire.sparouting.runtime.rules.builtin.AllowAll
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class ExampleConfiguration {
  @Bean
  fun exampleConfig(): SinglePageApplicationConfig {
    return object : SinglePageApplicationConfig {
      override val application = BlogApplication
      override val rules = listOf(AllowAll())
    }
  }
}
