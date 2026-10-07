package com.sparouting.examples.blog

import com.sparouting.contract.AccessDecision
import com.sparouting.examples.generated.routes.BlogRoutes
import com.sparouting.examples.generated.routes.blog.EditPostAccessHandler
import com.sparouting.examples.generated.routes.blog.EditPostRequest

class CheckEditPostAccess(private val posts: BlogPostService) : EditPostAccessHandler() {
  override fun evaluate(request: EditPostRequest): AccessDecision {
    if (posts.find(request.postId) == null) {
      return AccessDecision.Denied(
        destination = BlogRoutes.NotFound()
      )
    }

    return AccessDecision.Allowed
  }
}
