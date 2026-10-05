package com.sparouting.examples.spring.blog

import com.sparouting.contract.AccessDecision
import com.sparouting.examples.generated.routes.BlogRoutes
import com.sparouting.examples.generated.routes.blog.EditPostAccessHandler
import com.sparouting.examples.generated.routes.blog.EditPostRequest
import org.springframework.stereotype.Component

@Component
class CheckEditPostAccess(private val posts: BlogPostStore) : EditPostAccessHandler() {
  override fun evaluate(request: EditPostRequest): AccessDecision {
    if (posts.find(request.postId) == null) {
      return AccessDecision.Redirect(BlogRoutes.NotFound())
    }

    return AccessDecision.Allow
  }
}
