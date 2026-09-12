package com.sparouting.contract.codegen

import com.sparouting.contract.routeParameterIdentifier

internal fun String.toKotlinIdentifier(): String {
  val identifier = routeParameterIdentifier()
  return if (identifier in KOTLIN_KEYWORDS) "`$identifier`" else identifier
}

internal fun String.toKotlinStringLiteral(): String = buildString {
  append('"')
  this@toKotlinStringLiteral.forEach { character ->
    append(when (character) {
      '\\' -> "\\\\"
      '"' -> "\\\""
      '$' -> "\\$"
      '\n' -> "\\n"
      '\r' -> "\\r"
      '\t' -> "\\t"
      else -> if (character < ' ') "\\u${character.code.toString(16).padStart(4, '0')}" else character.toString()
    })
  }
  append('"')
}

private val KOTLIN_KEYWORDS = setOf(
  "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in",
  "interface", "is", "null", "object", "package", "return", "super", "this", "throw",
  "true", "try", "typealias", "typeof", "val", "var", "when", "while"
)
