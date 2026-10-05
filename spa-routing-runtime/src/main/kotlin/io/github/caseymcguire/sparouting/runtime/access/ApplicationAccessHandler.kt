package io.github.caseymcguire.sparouting.runtime.access

import com.sparouting.contract.AccessDecision
import io.github.caseymcguire.sparouting.runtime.request.RouteRequest

/** Checks access to an application before any route-specific access handler runs. */
fun interface ApplicationAccessHandler {
  fun evaluate(request: RouteRequest): AccessDecision
}
