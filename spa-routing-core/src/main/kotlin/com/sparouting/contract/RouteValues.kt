package com.sparouting.contract

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal val PATH_PARAMETER_PATTERN = "\\{([^}:]+)(?::[^}]*)?\\}".toRegex()

internal fun List<RouteParameter>.hasValidPathValues(values: Map<String, String>): Boolean {
  val names = map { it.name }.toSet()
  return values.keys.all { it in names } && all { it.optional || it.name in values }
}

internal fun List<RouteParameter>.hasValidQueryValues(values: Map<String, List<String>>): Boolean {
  return all { parameter ->
    val count = values[parameter.name]?.size ?: 0
    (parameter.optional || count > 0) && (parameter.repeated || count <= 1)
  }
}

internal fun List<RouteParameter>.resolveQueryValues(routeId: String, values: Map<String, List<String>>): String {
  require(hasValidQueryValues(values)) {
    "Invalid query parameters for SPA route $routeId"
  }
  return values.flatMap { (name, entries) ->
    entries.map { value ->
      "${URLEncoder.encode(name, StandardCharsets.UTF_8)}=${URLEncoder.encode(value, StandardCharsets.UTF_8)}"
    }
  }.joinToString("&")
}

internal fun resolveRoutePath(pathPattern: String, values: Map<String, String>): String {
  return PATH_PARAMETER_PATTERN.replace(pathPattern) { match ->
    values[match.groupValues[1]] ?: match.value
  }
}
