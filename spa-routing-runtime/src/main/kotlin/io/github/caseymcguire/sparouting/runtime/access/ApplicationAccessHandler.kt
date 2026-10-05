package io.github.caseymcguire.sparouting.runtime.access

import com.sparouting.contract.AccessDecision
import com.sparouting.contract.SinglePageApplicationDefinition
import io.github.caseymcguire.sparouting.runtime.request.RouteRequest

/** Checks access to an application before any route-specific access handler runs. */
abstract class ApplicationAccessHandler(val application: SinglePageApplicationDefinition) {
  abstract fun evaluate(request: RouteRequest): AccessDecision
}
