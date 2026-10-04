package com.sparouting.examples.spring

import com.sparouting.contract.applications.BlogSpaApplication
import io.github.caseymcguire.sparouting.spring.config.SinglePageApplicationConfig
import io.github.caseymcguire.sparouting.spring.rules.builtin.AllowAll
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class ExampleSpaConfiguration {
  @Bean
  fun exampleSpaConfig(): SinglePageApplicationConfig {
    return object : SinglePageApplicationConfig {
      override val application = BlogSpaApplication
      override val rules = listOf(AllowAll())
    }
  }
}
