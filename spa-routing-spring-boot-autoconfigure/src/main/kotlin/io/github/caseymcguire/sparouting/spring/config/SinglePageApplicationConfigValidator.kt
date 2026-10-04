package io.github.caseymcguire.sparouting.spring.config

import com.sparouting.contract.SinglePageApplicationDefinitionValidator

class SinglePageApplicationConfigValidator private constructor() {
  companion object {
    fun validate(routeConfigs: List<SinglePageApplicationConfig>) {
      SinglePageApplicationDefinitionValidator.validate(routeConfigs.map { it.application })
    }
  }
}
