package com.sparouting.examples.spring.blog

import com.sparouting.contract.AccessDecision
import com.sparouting.examples.generated.routes.BlogApplicationAccessHandler
import com.sparouting.contract.RouteRequest
import org.springframework.stereotype.Component

/** The example blog is public; post-specific checks run after this application check. */
@Component
class CheckBlogAccess : BlogApplicationAccessHandler() {
  override fun evaluate(request: RouteRequest): AccessDecision {
    return AccessDecision.Allow
  }
}
