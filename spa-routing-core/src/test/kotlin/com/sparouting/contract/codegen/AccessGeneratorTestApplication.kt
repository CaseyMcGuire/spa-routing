package com.sparouting.contract.codegen

import com.sparouting.contract.SpaApplicationDefinition
import com.sparouting.contract.parameter
import com.sparouting.contract.route

object AccessGeneratorTestApplication : SpaApplicationDefinition {
  override val id = "accesstest"
  override val name = "AccessTest"
  override val urlPrefix = "access"
  override val appRootPath = "src/test"
  override val routes = listOf(
    route("public", "Public"),
    route("start", "Start", generateAccessHandler = true),
    route(
      "posts/{postId}", "Post",
      queryString = listOf(
        parameter("q"), parameter("sort").optional(),
        parameter("tag").repeated(), parameter("filter").repeated().optional()
      ),
      generateAccessHandler = true
    ),
    route(
      "optional/{id}", "Optional",
      parameters = listOf(parameter("id").optional()),
      generateAccessHandler = true
    ),
    route(
      "names/{context}/{queryString}/{class}", "Names",
      queryString = listOf(parameter("class").optional()),
      generateAccessHandler = true
    )
  )
}
