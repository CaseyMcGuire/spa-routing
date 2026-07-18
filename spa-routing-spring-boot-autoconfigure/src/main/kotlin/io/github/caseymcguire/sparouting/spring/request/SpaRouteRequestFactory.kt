package io.github.caseymcguire.sparouting.spring.request

import com.sparouting.contract.SpaRouteDefinition
import io.github.caseymcguire.sparouting.spring.config.SinglePageApplicationConfig
import org.springframework.web.servlet.function.ServerRequest

interface SpaRouteRequestFactory {
  fun create(
    serverRequest: ServerRequest,
    application: SinglePageApplicationConfig,
    route: SpaRouteDefinition
  ): SpaRouteRequest
}
