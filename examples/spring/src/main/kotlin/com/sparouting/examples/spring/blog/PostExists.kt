package com.sparouting.examples.spring.blog

import com.sparouting.examples.generated.routes.BlogRoutes
import io.github.caseymcguire.sparouting.spring.request.SpaRouteRequest
import io.github.caseymcguire.sparouting.spring.rules.SpaRouteRule
import io.github.caseymcguire.sparouting.spring.rules.SpaRouteRuleAction
import io.github.caseymcguire.sparouting.spring.rules.SpaRouteRuleResult
import org.springframework.stereotype.Component

@Component
class PostExists(private val posts: BlogPostStore) : SpaRouteRule {
  override fun evaluate(request: SpaRouteRequest): SpaRouteRuleResult {
    val postId = request.pathParameter("postId")
    if (postId != null && posts.find(postId) != null) {
      return SpaRouteRuleResult.Skip
    }

    return SpaRouteRuleResult.Deny(
      SpaRouteRuleAction.redirectTo(BlogRoutes.NotFound())
    )
  }
}
