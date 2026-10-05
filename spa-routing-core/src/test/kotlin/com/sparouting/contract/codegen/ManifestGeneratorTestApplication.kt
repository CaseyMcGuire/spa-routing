package com.sparouting.contract.codegen

import com.sparouting.contract.RouteDefinition
import com.sparouting.contract.SinglePageApplicationDefinition
import com.sparouting.contract.parameter
import com.sparouting.contract.route

object ManifestGeneratorTestApplication : SinglePageApplicationDefinition {
  override val id = "manifesttest"
  override val name = "Manifest Test"
  override val urlPrefix = ""
  override val appRootPath = "build-only/frontend/index.tsx"
  override val bundleName = "custom-assets"
  override val routes = listOf(
    route("", "Index"),
    route("posts/{postId}", "Post", queryString = listOf(parameter("tag").repeated()), generateAccessHandler = true)
  )
}

// Generated manifest class names must not shadow the contract types they use.
object RouteManifestGeneratorTestApplication : SinglePageApplicationDefinition {
  override val id = "routemanifesttest"
  override val name = "Route"
  override val urlPrefix = "collision"
  override val appRootPath = "src/test"
  override val routes = listOf(route("", "Index"))
}

object ApplicationManifestGeneratorTestApplication : SinglePageApplicationDefinition {
  override val id = "applicationmanifesttest"
  override val name = "SinglePageApplication"
  override val urlPrefix = "empty"
  override val appRootPath = "src/test"
  override val routes = emptyList<RouteDefinition>()
}
