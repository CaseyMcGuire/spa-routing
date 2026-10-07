package com.sparouting.contract.codegen

import com.sparouting.contract.SinglePageApplicationDefinition
import com.sparouting.contract.parameter
import com.sparouting.contract.route

/** Routes used to check generated contexts and TypeScript preload inference. */
object PreloadPrototypeApplication : SinglePageApplicationDefinition {
  override val id = "preloadprototype"
  override val name = "PreloadPrototype"
  override val urlPrefix = "wiki"
  override val appRootPath = "src/test"
  override val routes = listOf(
    route("about", "About"),
    route(
      "{wikiId}", "View",
      queryString = listOf(parameter("revision").optional(), parameter("tag").repeated()),
      generateAccessHandler = true
    ),
    route("{wikiId}/edit", "Edit", queryString = listOf(parameter("draft"))),
    route(
      "{wikiId}/sections/{section}", "Section",
      parameters = listOf(parameter("wikiId"), parameter("section").optional()),
      queryString = listOf(parameter("filter").repeated().optional())
    )
  )
}
