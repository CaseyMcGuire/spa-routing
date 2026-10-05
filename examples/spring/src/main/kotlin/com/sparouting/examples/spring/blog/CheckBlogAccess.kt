package com.sparouting.examples.spring.blog

import com.sparouting.contract.AccessDecision
import com.sparouting.contract.applications.BlogApplication
import io.github.caseymcguire.sparouting.runtime.access.ApplicationAccessHandler
import io.github.caseymcguire.sparouting.runtime.request.RouteRequest
import org.springframework.stereotype.Component

/** The example blog is public; post-specific checks run after this application check. */
@Component
class CheckBlogAccess : ApplicationAccessHandler(BlogApplication) {
  override fun evaluate(request: RouteRequest): AccessDecision {
    return AccessDecision.Allow
  }
}
