package io.github.caseymcguire.sparouting.spring.config

import com.sparouting.contract.SinglePageApplicationDefinition
import com.sparouting.contract.RouteDefinition
import io.github.caseymcguire.sparouting.spring.rules.SpaRouteRule
import org.springframework.web.servlet.function.ServerResponse

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

  val rules: List<SpaRouteRule>
    get() = emptyList()

  /**
   * Optional per-application HTML override.
   *
   * Return null to use the configured SpaHtmlRenderer bean.
   */
  fun renderHtml(): ServerResponse? = null

  fun getFullPathPatterns(): List<String> {
    return routes.map { getFullPathPattern(it) }
  }

  fun getFullPathPattern(route: RouteDefinition): String {
    return application.getFullPathPattern(route)
  }
}
