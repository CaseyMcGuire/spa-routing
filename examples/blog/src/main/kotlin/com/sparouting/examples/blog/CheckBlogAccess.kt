package com.sparouting.examples.blog

import com.sparouting.contract.AccessDecision
import com.sparouting.contract.RouteRequest
import com.sparouting.examples.generated.routes.BlogApplicationAccessHandler

/** The example blog is public; post-specific checks run after this application check. */
class CheckBlogAccess : BlogApplicationAccessHandler() {
  override fun evaluate(request: RouteRequest): AccessDecision {
    return AccessDecision.Allowed
  }
}
