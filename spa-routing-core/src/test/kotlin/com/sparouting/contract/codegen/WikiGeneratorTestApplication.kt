package com.sparouting.contract.codegen

import com.sparouting.contract.SinglePageApplicationDefinition
import com.sparouting.contract.route
import com.sparouting.contract.parameter

object WikiGeneratorTestApplication : SinglePageApplicationDefinition {
  override val id = "wiki"
  override val name = "Wiki"
  override val urlPrefix = "wiki"
  override val appRootPath = "src/test"
  override val routes = listOf(
    route("", "Index"),
    route("{wikiId}", "View", queryString = listOf(parameter("tab").optional())),
    route("{wikiId}/edit", "Edit")
  )
}
