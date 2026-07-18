package io.github.caseymcguire.sparouting.spring.config

import com.sparouting.contract.SpaRouteDefinition

data class SinglePageApplicationRouteRegistration(
  val application: SinglePageApplicationConfig,
  val route: SpaRouteDefinition
)
