package com.sparouting.runtime.access

import com.sparouting.contract.AccessDecision
import com.sparouting.contract.SinglePageApplicationManifest
import com.sparouting.runtime.request.RouteRequest

/** Checks access to an application before any route-specific access handler runs. */
abstract class ApplicationAccessHandler(val manifest: SinglePageApplicationManifest) {
  abstract fun evaluate(request: RouteRequest): AccessDecision
}
