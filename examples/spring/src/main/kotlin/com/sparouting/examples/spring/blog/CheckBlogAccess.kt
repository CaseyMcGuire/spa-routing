package com.sparouting.examples.spring.blog

import com.sparouting.contract.AccessDecision
import com.sparouting.examples.generated.routes.BlogManifest
import com.sparouting.runtime.access.ApplicationAccessHandler
import com.sparouting.runtime.request.RouteRequest
import org.springframework.stereotype.Component

/** The example blog is public; post-specific checks run after this application check. */
@Component
class CheckBlogAccess(manifest: BlogManifest) : ApplicationAccessHandler(manifest) {
  override fun evaluate(request: RouteRequest): AccessDecision {
    return AccessDecision.Allow
  }
}
