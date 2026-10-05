package io.github.caseymcguire.sparouting.runtime.config

import com.sparouting.contract.RouteAccessHandler
import com.sparouting.contract.RouteManifest
import io.github.caseymcguire.sparouting.runtime.access.ApplicationAccessHandler

data class SinglePageApplicationRouteRegistration(
  val application: SinglePageApplicationConfig,
  val route: RouteManifest,
  val applicationAccessHandler: ApplicationAccessHandler,
  val routeAccessHandler: RouteAccessHandler<*>?
)
