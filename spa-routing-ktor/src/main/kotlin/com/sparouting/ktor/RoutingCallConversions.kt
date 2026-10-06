package com.sparouting.ktor

import io.ktor.server.routing.RoutingCall

internal fun RoutingCall.toRoutePathParameters(): Map<String, String> {
  return pathParameters.entries().associate { (name, values) ->
    name to values.firstOrNull().orEmpty()
  }
}

internal fun RoutingCall.toRouteQueryString(): Map<String, List<String>> {
  return request.queryParameters.entries().associate { (name, values) -> name to values }
}

internal fun RoutingCall.toRouteHeaders(): Map<String, List<String>> {
  return request.headers.entries().associate { (name, values) -> name to values }
}
