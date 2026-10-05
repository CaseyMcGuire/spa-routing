package com.sparouting.spring.request

import com.sparouting.contract.RouteManifest
import com.sparouting.runtime.config.SinglePageApplicationConfig
import com.sparouting.runtime.request.RouteRequest
import org.springframework.web.servlet.function.ServerRequest

interface RouteRequestFactory {
  fun create(
    serverRequest: ServerRequest,
    application: SinglePageApplicationConfig,
    route: RouteManifest
  ): RouteRequest
}
