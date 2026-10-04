package com.sparouting.contract

data class RouteParameter(
  val name: String,
  val optional: Boolean = false,
  val repeated: Boolean = false
) {
  init {
    require(name.isNotBlank()) {
      "SPA route parameter name must not be blank."
    }
  }

  fun optional(): RouteParameter {
    return copy(optional = true)
  }

  fun repeated(): RouteParameter {
    return copy(repeated = true)
  }
}
