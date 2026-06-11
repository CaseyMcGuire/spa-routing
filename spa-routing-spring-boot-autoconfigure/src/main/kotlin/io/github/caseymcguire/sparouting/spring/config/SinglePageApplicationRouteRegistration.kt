package io.github.caseymcguire.sparouting.spring.config

import com.caseymcguiredotcom.sparoutecontract.SpaRouteDefinition

data class SinglePageApplicationRouteRegistration(
  val application: SinglePageApplicationConfig,
  val route: SpaRouteDefinition
)
