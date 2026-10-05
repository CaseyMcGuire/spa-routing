package com.sparouting.contract

/** Runtime route metadata. [path] is the full path pattern, including the application prefix. */
data class RouteManifest(
  val path: String,
  val id: String,
  val parameters: List<RouteParameter> = PATH_PARAMETER_PATTERN.findAll(path)
    .map { RouteParameter(it.groupValues[1]) }.toList(),
  val queryString: List<RouteParameter> = emptyList(),
  val hasAccessHandler: Boolean = false
) {
  fun hasValidParameterValues(values: Map<String, String>): Boolean = parameters.hasValidPathValues(values)

  fun hasValidQueryStringValues(values: Map<String, List<String>>): Boolean = queryString.hasValidQueryValues(values)

  fun resolvePath(values: Map<String, String>): String = resolveRoutePath(path, values)

  fun resolveQueryString(values: Map<String, List<String>>): String = queryString.resolveQueryValues(id, values)
}
