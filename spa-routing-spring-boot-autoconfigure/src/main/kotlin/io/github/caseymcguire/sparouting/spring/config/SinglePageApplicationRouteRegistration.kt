package io.github.caseymcguire.sparouting.spring.config

import com.sparouting.contract.RouteDefinition

data class SinglePageApplicationRouteRegistration(
  val application: SinglePageApplicationConfig,
  val route: RouteDefinition
)
