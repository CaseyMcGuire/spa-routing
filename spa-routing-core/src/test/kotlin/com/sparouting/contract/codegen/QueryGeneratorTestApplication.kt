package com.sparouting.contract.codegen

import com.sparouting.contract.SpaApplicationDefinition
import com.sparouting.contract.route
import com.sparouting.contract.parameter

object QueryGeneratorTestApplication : SpaApplicationDefinition {
  override val id = "querytest"
  override val name = "QueryTest"
  override val urlPrefix = "querytest"
  override val appRootPath = "src/test"
  override val routes = listOf(
    route("home", "Home"),
    route("queryString", "QueryString", queryString = listOf(parameter("q"))),
    route("queryString-key", "QueryStringKey", queryString = listOf(parameter("q"))),
    route("users/{id}", "UserDetail", queryString = listOf(
      parameter("foo"), parameter("baz").optional(), parameter("tag").repeated().optional()
    )),
    route("search", "Search", queryString = listOf(parameter("q"))),
    route("filters", "Filters", queryString = listOf(parameter("tag").repeated())),
    route("optional", "OptionalQuery", queryString = listOf(parameter("foo").optional(), parameter("tag").repeated().optional())),
    route("optional/{id}", "OptionalMixed", queryString = listOf(parameter("foo").optional())),
    route("same/{queryString}/{queryString_}", "SameName", queryString = listOf(parameter("queryString"))),
    route("special", "Special", queryString = listOf(
      parameter("a b").repeated(), parameter("class").optional(), parameter("__proto__").optional(), parameter("a\"$\n").optional()
    ))
  )
}
