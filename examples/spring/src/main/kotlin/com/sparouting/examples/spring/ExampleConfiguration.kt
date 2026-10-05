package com.sparouting.examples.spring

import com.sparouting.examples.generated.routes.BlogManifest
import com.sparouting.runtime.config.SinglePageApplicationConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class ExampleConfiguration {
  @Bean
  fun blogManifest(): BlogManifest = BlogManifest()

  @Bean
  fun exampleConfig(blogManifest: BlogManifest): SinglePageApplicationConfig {
    return object : SinglePageApplicationConfig {
      override val manifest = blogManifest
    }
  }
}
