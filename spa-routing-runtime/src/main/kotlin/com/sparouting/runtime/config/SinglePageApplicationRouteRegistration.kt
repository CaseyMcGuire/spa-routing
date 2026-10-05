package com.sparouting.runtime.config

import com.sparouting.contract.RouteAccessHandler
import com.sparouting.contract.RouteManifest
import com.sparouting.runtime.access.ApplicationAccessHandler

data class SinglePageApplicationRouteRegistration(
  val application: SinglePageApplicationConfig,
  val route: RouteManifest,
  val applicationAccessHandler: ApplicationAccessHandler,
  val routeAccessHandler: RouteAccessHandler<*>?
)
