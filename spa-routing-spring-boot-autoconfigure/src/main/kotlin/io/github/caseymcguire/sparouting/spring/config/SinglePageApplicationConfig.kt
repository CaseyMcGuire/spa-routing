package io.github.caseymcguire.sparouting.spring.config

import com.caseymcguiredotcom.sparoutecontract.SpaApplicationDefinition
import com.caseymcguiredotcom.sparoutecontract.SpaRouteDefinition
import com.caseymcguiredotcom.sparoutecontract.SpaRouteKey
import io.github.caseymcguire.sparouting.spring.rules.SpaRouteRule
import org.springframework.web.servlet.function.ServerResponse

interface SinglePageApplicationConfig {
  val application: SpaApplicationDefinition

  val routes: List<SpaRouteDefinition>
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

  val routeRules: Map<SpaRouteKey, List<SpaRouteRule>>
    get() = emptyMap()

  /**
   * Optional per-application HTML override.
   *
   * Return null to use the configured SpaHtmlRenderer bean.
   */
  fun renderHtml(): ServerResponse? = null

  fun getFullUrls(): List<String> {
    return routes.map { getFullUrl(it.path) }
  }

  fun getFullUrl(route: SpaRouteDefinition): String {
    return getFullUrl(route.path)
  }

  fun getFullUrl(path: String): String {
    return application.getFullUrl(path)
  }

  fun getRouteRules(route: SpaRouteDefinition): List<SpaRouteRule> {
    return routeRules.entries
      .firstOrNull {
        it.key.applicationId == applicationId && it.key.routeId == route.id
      }
      ?.value
      .orEmpty()
  }
}
