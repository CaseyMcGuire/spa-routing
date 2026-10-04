package io.github.caseymcguire.sparouting.spring.request

import com.sparouting.contract.RouteDefinition
import io.github.caseymcguire.sparouting.runtime.config.SinglePageApplicationConfig
import io.github.caseymcguire.sparouting.runtime.request.RouteRequest
import org.springframework.web.servlet.function.ServerRequest

interface RouteRequestFactory {
  fun create(
    serverRequest: ServerRequest,
    application: SinglePageApplicationConfig,
    route: RouteDefinition
  ): RouteRequest
}
