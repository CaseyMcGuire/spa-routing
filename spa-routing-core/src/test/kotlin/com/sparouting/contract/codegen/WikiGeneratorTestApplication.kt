package com.sparouting.contract.codegen

import com.sparouting.contract.SpaApplicationDefinition
import com.sparouting.contract.route
import com.sparouting.contract.string

object WikiGeneratorTestApplication : SpaApplicationDefinition {
  override val id = "wiki"
  override val name = "Wiki"
  override val urlPrefix = "wiki"
  override val appRootPath = "src/test"
  override val routes = listOf(
    route("", "Index"),
    route("{wikiId}", "View", listOf(string("wikiId")), listOf(string("tab").optional())),
    route("{wikiId}/edit", "Edit", listOf(string("wikiId")))
  )
}
