package com.sparouting.examples.spring.blog

import com.sparouting.contract.RouteDecision
import com.sparouting.examples.generated.routes.BlogRoutes
import com.sparouting.examples.generated.routes.blog.EditPostAccessHandler
import com.sparouting.examples.generated.routes.blog.EditPostRequest
import org.springframework.stereotype.Component

@Component
class CheckEditPostAccess(private val posts: BlogPostStore) : EditPostAccessHandler() {
  override fun evaluate(request: EditPostRequest): RouteDecision {
    if (posts.find(request.postId) == null) {
      return RouteDecision.Redirect(BlogRoutes.NotFound())
    }

    return RouteDecision.Allow
  }
}
