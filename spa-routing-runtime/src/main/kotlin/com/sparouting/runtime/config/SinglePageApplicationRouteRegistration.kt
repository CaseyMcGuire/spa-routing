package com.sparouting.runtime.config

import com.sparouting.contract.ApplicationAccessHandler
import com.sparouting.contract.RouteAccessHandler
import com.sparouting.contract.RouteManifest
import com.sparouting.contract.SinglePageApplicationConfig

internal data class SinglePageApplicationRouteRegistration(
  val application: SinglePageApplicationConfig,
  val route: RouteManifest,
  val applicationAccessHandler: ApplicationAccessHandler<*>,
  val routeAccessHandler: RouteAccessHandler<*>?
)
