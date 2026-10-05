package com.sparouting.contract.codegen

import com.sparouting.contract.RouteDefinition
import com.sparouting.contract.SinglePageApplicationDefinition
import com.sparouting.contract.parameter
import com.sparouting.contract.route

object ConfigGeneratorTestApplication : SinglePageApplicationDefinition {
  override val id = "configtest"
  override val name = "Config Test"
  override val urlPrefix = ""
  override val appRootPath = "build-only/frontend/index.tsx"
  override val bundleName = "custom-assets"
  override val routes = listOf(
    route("", "Index"),
    route("posts/{postId}", "Post", queryString = listOf(parameter("tag").repeated()), generateAccessHandler = true),
    route("class", "Class", generateAccessHandler = true),
    route("handlers", "Handlers", generateAccessHandler = true)
  )
}

// Generated config names must not shadow runtime types.
object RouteConfigGeneratorTestApplication : SinglePageApplicationDefinition {
  override val id = "routeconfigtest"
  override val name = "Route"
  override val urlPrefix = "collision"
  override val appRootPath = "src/test"
  override val routes = listOf(route("", "Index"))
}

object ApplicationConfigGeneratorTestApplication : SinglePageApplicationDefinition {
  override val id = "applicationconfigtest"
  override val name = "SinglePage"
  override val urlPrefix = "empty"
  override val appRootPath = "src/test"
  override val routes = emptyList<RouteDefinition>()
}
