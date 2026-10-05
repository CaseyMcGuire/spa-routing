package io.github.caseymcguire.sparouting.runtime.config

import com.sparouting.contract.RouteAccessHandler
import com.sparouting.contract.RouteDefinition
import io.github.caseymcguire.sparouting.runtime.access.ApplicationAccessHandler

data class SinglePageApplicationRouteRegistration(
  val application: SinglePageApplicationConfig,
  val route: RouteDefinition,
  val applicationAccessHandler: ApplicationAccessHandler,
  val routeAccessHandler: RouteAccessHandler<*>?
)
