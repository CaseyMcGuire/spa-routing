package com.sparouting.contract.applications

import com.sparouting.contract.SinglePageApplicationDefinition
import com.sparouting.contract.parameter
import com.sparouting.contract.route

/** Blog routes shared by the example server applications and generated clients. */
object BlogApplication : SinglePageApplicationDefinition {
  override val id = "blog"
  override val name = "Blog"
  override val urlPrefix = ""
  override val appRootPath = "frontend/src/main.tsx"

  override val routes = listOf(
    route("", "Index", queryString = listOf(parameter("q").optional())),
    route("posts/{postId}", "Post", generateAccessHandler = true),
    route("new", "NewPost"),
    route("posts/{postId}/edit", "EditPost", generateAccessHandler = true),
    route("not-found", "NotFound"),
    route("error", "Error")
  )
}
