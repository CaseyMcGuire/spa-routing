package com.sparouting.contract

data class RouteDefinition(
  val path: String,
  val id: String,
  /** String parameters inferred from path placeholders; override to declare optional path values. */
  val parameters: List<RouteParameter> = inferPathParameters(path),
  /** Declared query-string fields, including whether each is optional or repeated. */
  val queryString: List<RouteParameter> = emptyList(),
  /**
   * Generate a typed access-handler base class and require an implementation at runtime.
   * Also exposes hasAccessHandler on the generated client route; application access checks are separate.
   */
  val generateAccessHandler: Boolean = false
) {
  init {
    require(id.isNotBlank()) {
      "SPA route id must not be blank."
    }

    require(ROUTE_ID_PATTERN.matches(id)) {
      "SPA route id must be a PascalCase Kotlin identifier: $id"
    }

    require(parameters.none { it.repeated }) {
      "Route $id cannot have repeated path parameters. Use repeated() only for query parameters."
    }

    val queryNames = queryString.map { it.name }
    require(queryNames.toSet().size == queryNames.size) {
      "Route $id has duplicate query parameter metadata: ${queryNames.joinToString(", ")}"
    }
    requireUniqueGeneratedNames(parameters, "path", false)
    requireUniqueGeneratedNames(queryString, "query", false)
    requireUniqueGeneratedNames(queryString, "query enum", true)

    val pathParameterNames = pathParameterNames()
    val routeParameterNames = parameters.map { it.name }

    require(routeParameterNames.toSet().size == routeParameterNames.size) {
      "Route $id has duplicate parameter metadata: ${routeParameterNames.joinToString(", ")}"
    }

    val routeParameterNameSet = routeParameterNames.toSet()
    require(pathParameterNames == routeParameterNameSet) {
      buildString {
        append("Route $id has explicit parameter metadata that does not match its path parameters.")

        val missingParameters = pathParameterNames - routeParameterNameSet
        if (missingParameters.isNotEmpty()) {
          append(" Missing: ${missingParameters.joinToString(", ")}.")
        }

        val extraParameters = routeParameterNameSet - pathParameterNames
        if (extraParameters.isNotEmpty()) {
          append(" Extra: ${extraParameters.joinToString(", ")}.")
        }
      }
    }
  }

  fun hasValidParameterValues(parameterValues: Map<String, String>): Boolean {
    return parameters.hasValidPathValues(parameterValues)
  }

  fun requiredParameters(): List<RouteParameter> {
    return parameters.filter { !it.optional }
  }

  fun hasValidQueryStringValues(parameterValues: Map<String, List<String>>): Boolean {
    return queryString.hasValidQueryValues(parameterValues)
  }

  fun resolveQueryString(parameterValues: Map<String, List<String>>): String {
    return queryString.resolveQueryValues(id, parameterValues)
  }

  private fun requireUniqueGeneratedNames(
    declarations: List<RouteParameter>,
    kind: String,
    enumNames: Boolean
  ) {
    val names = declarations.map {
      if (enumNames) it.name.queryStringKeyIdentifier() else it.name.routeParameterIdentifier()
    }
    require(names.toSet().size == names.size) {
      "Route $id has colliding generated $kind identifiers: ${names.joinToString(", ")}"
    }
  }

  fun resolvePath(pathPattern: String, parameterValues: Map<String, String>): String {
    return resolveRoutePath(pathPattern, parameterValues)
  }

  private fun pathParameterNames(): Set<String> {
    val names = PATH_PARAMETER_PATTERN
      .findAll(path)
      .map { it.groupValues[1] }
      .toList()

    require(names.toSet().size == names.size) {
      "Route $id has duplicate path parameters: ${names.joinToString(", ")}"
    }

    return names.toSet()
  }

  companion object {
    private val ROUTE_ID_PATTERN = "[A-Z][A-Za-z0-9]*".toRegex()

    internal fun inferPathParameters(path: String): List<RouteParameter> {
      return PATH_PARAMETER_PATTERN.findAll(path)
        .map { RouteParameter(it.groupValues[1]) }
        .toList()
    }
  }
}
