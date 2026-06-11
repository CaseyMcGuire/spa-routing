package io.github.caseymcguire.sparouting.spring.request

import com.caseymcguiredotcom.sparoutecontract.SpaRouteDefinition
import io.github.caseymcguire.sparouting.spring.config.SinglePageApplicationConfig
import org.springframework.web.servlet.function.ServerRequest

interface SpaRouteRequestFactory {
  fun create(
    serverRequest: ServerRequest,
    application: SinglePageApplicationConfig,
    route: SpaRouteDefinition
  ): SpaRouteRequest
}
