package com.sparouting.examples.spring

import com.sparouting.contract.SpaRouteKey
import com.sparouting.contract.applications.BlogSpaApplication
import com.sparouting.examples.generated.routes.BlogRoutes
import com.sparouting.examples.spring.blog.PostExists
import io.github.caseymcguire.sparouting.spring.config.SinglePageApplicationConfig
import io.github.caseymcguire.sparouting.spring.rules.SpaRouteRule
import io.github.caseymcguire.sparouting.spring.rules.builtin.AllowAll
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class ExampleSpaConfiguration(private val postExists: PostExists) {
  @Bean
  fun exampleSpaConfig(): SinglePageApplicationConfig {
    return object : SinglePageApplicationConfig {
      override val application = BlogSpaApplication
      override val rules = listOf(AllowAll())
      override val routeRules: Map<SpaRouteKey, List<SpaRouteRule>> = mapOf(
        BlogRoutes.Post to listOf(postExists),
        BlogRoutes.EditPost to listOf(postExists)
      )
    }
  }
}
