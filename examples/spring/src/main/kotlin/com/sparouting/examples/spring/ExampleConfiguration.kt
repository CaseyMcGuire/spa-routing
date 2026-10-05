package com.sparouting.examples.spring

import com.sparouting.contract.applications.BlogApplication
import com.sparouting.examples.spring.blog.CheckBlogAccess
import io.github.caseymcguire.sparouting.runtime.config.SinglePageApplicationConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class ExampleConfiguration {
  @Bean
  fun exampleConfig(checkBlogAccess: CheckBlogAccess): SinglePageApplicationConfig {
    return object : SinglePageApplicationConfig {
      override val application = BlogApplication
      override val accessHandler = checkBlogAccess
    }
  }
}
