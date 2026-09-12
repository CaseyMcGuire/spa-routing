package com.sparouting.contract

import java.util.Locale

internal fun String.routeParameterIdentifier(): String {
  val sanitized = replace("[^A-Za-z0-9_]".toRegex(), "_")
  return when {
    sanitized.all { it == '_' } -> "parameter$sanitized"
    sanitized.first().isDigit() -> "_$sanitized"
    else -> sanitized
  }
}

internal fun String.queryKeyIdentifier(): String = routeParameterIdentifier().uppercase(Locale.ROOT)
