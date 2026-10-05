package com.sparouting.examples.spring.blog

import com.sparouting.contract.AccessDecision
import com.sparouting.examples.generated.routes.BlogRoutes
import com.sparouting.examples.generated.routes.blog.PostAccessHandler
import com.sparouting.examples.generated.routes.blog.PostRequest
import org.springframework.stereotype.Component

@Component
class CheckPostAccess(private val posts: BlogPostStore) : PostAccessHandler() {
  override fun evaluate(request: PostRequest): AccessDecision {
    if (posts.find(request.postId) == null) {
      return AccessDecision.Redirect(BlogRoutes.NotFound())
    }

    return AccessDecision.Allow
  }
}
