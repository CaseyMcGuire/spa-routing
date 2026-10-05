package com.sparouting.examples.spring

import com.sparouting.examples.blog.BlogPostService
import com.sparouting.examples.blog.CheckBlogAccess
import com.sparouting.examples.blog.CheckEditPostAccess
import com.sparouting.examples.blog.CheckPostAccess
import com.sparouting.examples.generated.routes.BlogApplicationConfig
import com.sparouting.examples.generated.routes.BlogRouteAccessHandlers
import com.sparouting.examples.generated.routes.blog.EditPostAccessHandler
import com.sparouting.examples.generated.routes.blog.PostAccessHandler
import com.sparouting.runtime.rendering.HtmlDocumentRenderer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class ExampleConfiguration {
  @Bean
  fun blogPostService(): BlogPostService = BlogPostService()

  @Bean
  fun checkBlogAccess(): CheckBlogAccess = CheckBlogAccess()

  @Bean
  fun checkPostAccess(posts: BlogPostService): CheckPostAccess = CheckPostAccess(posts)

  @Bean
  fun checkEditPostAccess(posts: BlogPostService): CheckEditPostAccess = CheckEditPostAccess(posts)

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
    routeAccessHandlers = routeAccessHandlers,
    htmlRenderer = HtmlDocumentRenderer(globalStylesheet = null)
  )
}
