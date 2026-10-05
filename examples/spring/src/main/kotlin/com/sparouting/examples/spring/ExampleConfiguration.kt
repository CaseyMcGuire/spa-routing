package com.sparouting.examples.spring

import com.sparouting.examples.generated.routes.BlogApplicationConfig
import com.sparouting.examples.generated.routes.BlogRouteAccessHandlers
import com.sparouting.examples.generated.routes.blog.EditPostAccessHandler
import com.sparouting.examples.generated.routes.blog.PostAccessHandler
import com.sparouting.examples.spring.blog.CheckBlogAccess
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class ExampleConfiguration {
  @Bean
  fun blogRouteAccessHandlers(
    post: PostAccessHandler,
    editPost: EditPostAccessHandler
  ): BlogRouteAccessHandlers = BlogRouteAccessHandlers(post = post, editPost = editPost)

  @Bean
  fun blogConfig(
    applicationAccessHandler: CheckBlogAccess,
    routeAccessHandlers: BlogRouteAccessHandlers
  ): BlogApplicationConfig = BlogApplicationConfig(
    applicationAccessHandler = applicationAccessHandler,
    routeAccessHandlers = routeAccessHandlers
  )
}
