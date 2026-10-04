package com.sparouting.contract.applications

import com.sparouting.contract.SpaApplicationDefinition
import com.sparouting.contract.parameter
import com.sparouting.contract.route

/** Blog routes shared by the example server applications and generated clients. */
object BlogSpaApplication : SpaApplicationDefinition {
  override val id = "blog"
  override val name = "Blog"
  override val urlPrefix = ""
  override val appRootPath = "src/main/resources/static/bundles/blog.bundle.js"

  override val routes = listOf(
    route("", "Index", queryString = listOf(parameter("q").optional())),
    route("posts/{postId}", "Post"),
    route("new", "NewPost"),
    route("posts/{postId}/edit", "EditPost"),
    route("not-found", "NotFound"),
    route("error", "Error")
  )
}
