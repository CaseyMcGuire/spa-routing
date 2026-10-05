package io.github.caseymcguire.sparouting.runtime.config

import com.sparouting.contract.RouteDefinition
import com.sparouting.contract.SinglePageApplicationDefinition

/** Application configuration shared by server adapters. Access handlers are registered separately. */
interface SinglePageApplicationConfig {
  val application: SinglePageApplicationDefinition

  val routes: List<RouteDefinition>
    get() = application.routes

  val name: String
    get() = application.name

  val urlPrefix: String
    get() = application.urlPrefix

  val appRootPath: String
    get() = application.appRootPath

  val bundleName: String
    get() = application.bundleName

  val applicationId: String
    get() = application.id

  fun getFullPathPatterns(): List<String> {
    return routes.map { getFullPathPattern(it) }
  }

  fun getFullPathPattern(route: RouteDefinition): String {
    return application.getFullPathPattern(route)
  }
}
