package com.sparouting.contract.codegen

import com.sparouting.contract.SpaApplicationDefinition
import com.sparouting.contract.route
import com.sparouting.contract.string

object QueryGeneratorTestApplication : SpaApplicationDefinition {
  override val id = "querytest"
  override val name = "QueryTest"
  override val urlPrefix = "querytest"
  override val appRootPath = "src/test"
  override val routes = listOf(
    route("home", "Home"),
    route("query", "Query", queryParameters = listOf(string("q"))),
    route("query-key", "QueryKey", queryParameters = listOf(string("q"))),
    route("users/{id}", "UserDetail", listOf(string("id")), listOf(
      string("foo"), string("baz").optional(), string("tag").repeated().optional()
    )),
    route("search", "Search", queryParameters = listOf(string("q"))),
    route("filters", "Filters", queryParameters = listOf(string("tag").repeated())),
    route("optional", "OptionalQuery", queryParameters = listOf(string("foo").optional(), string("tag").repeated().optional())),
    route("optional/{id}", "OptionalMixed", listOf(string("id")), listOf(string("foo").optional())),
    route("same/{query}/{query_}", "SameName", listOf(string("query"), string("query_")), listOf(string("query"))),
    route("special", "Special", queryParameters = listOf(
      string("a b").repeated(), string("class").optional(), string("__proto__").optional(), string("a\"$\n").optional()
    ))
  )
}
