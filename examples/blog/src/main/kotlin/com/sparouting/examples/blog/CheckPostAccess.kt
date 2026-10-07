package com.sparouting.examples.blog

import com.sparouting.contract.AccessDecision
import com.sparouting.contract.DenialReason
import com.sparouting.examples.generated.routes.BlogRoutes
import com.sparouting.examples.generated.routes.blog.PostAccessHandler
import com.sparouting.examples.generated.routes.blog.PostRequest

class CheckPostAccess(private val posts: BlogPostService) : PostAccessHandler() {
  override fun evaluate(request: PostRequest): AccessDecision {
    if (posts.find(request.postId) == null) {
      return AccessDecision.Denied(
        reason = DenialReason(code = "post_not_found", message = "That post could not be found."),
        destination = BlogRoutes.NotFound()
      )
    }

    return AccessDecision.Allowed
  }
}
