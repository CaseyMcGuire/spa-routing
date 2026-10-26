package com.sparouting.contract.codegen

import com.sparouting.contract.SinglePageApplicationDefinitionDiscovery
import com.sparouting.contract.RouteParameter
import com.sparouting.contract.queryStringKeyIdentifier
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

fun main() {
  generateClientRoutes()
  exitProcess(0)
}

internal fun generateClientRoutes() {
  val outputDirectoryPath = System.getProperty("route.output.dir")
    ?: throw IllegalArgumentException("'route.output.dir' must be set in task config")
  val configs = SinglePageApplicationDefinitionDiscovery.discoverFromSystemProperty()
  val routeConverter = RoutePathConverter()

  val configNameToTypeScriptObjectEntries = mutableMapOf<String, List<TypeScriptRouteConfig>>()
  for (config in configs) {
    val configName = config.name.replace(" ", "")
    if (configNameToTypeScriptObjectEntries.containsKey(configName)) {
      throw IllegalStateException("Duplicate config name: $configName")
    }

    val routeIds = mutableSetOf<String>()
    configNameToTypeScriptObjectEntries[configName] = config.routes.map { route ->
      val routeId = route.id
      if (!routeIds.add(routeId)) {
        throw IllegalStateException("Duplicate route id: ${route.id} in config: $configName")
      }
      TypeScriptRouteConfig(
        applicationId = config.id,
        key = routeId,
        path = routeConverter.convertToReactRouter(config.getFullPathPattern(route)),
        parameters = route.parameters,
        queryString = route.queryString,
        hasAccessHandler = route.generateAccessHandler
      )
    }
  }

  val fileNameToContent = configNameToTypeScriptObjectEntries.map { entry ->
    val typeName = "${entry.key}Route"
    val objectName = "${entry.key}Routes"
    val typescriptObjectEntries = entry.value.sortedBy { it.key }
    val runtimeImports = buildList {
      if (typescriptObjectEntries.any { it.parameters.isEmpty() && it.queryString.isEmpty() }) {
        add("routeWithoutParams")
      }
      if (typescriptObjectEntries.any { it.parameters.isNotEmpty() }) {
        add("encodeRouteParam")
      }
      if (typescriptObjectEntries.any { it.parameters.isNotEmpty() && it.queryString.isEmpty() }) {
        add("route")
      }
      if (typescriptObjectEntries.any { it.queryString.isNotEmpty() }) {
        addAll(listOf("appendQueryString", "createRouteParser", "readQueryString"))
      }
    }.sorted()

    objectName to buildString {
      appendLine("// THIS FILE IS GENERATED. DO NOT EDIT BY HAND.")
      appendLine("// Run './gradlew generateClientRoutes' to regenerate.")
      appendLine()
      if (runtimeImports.isNotEmpty()) {
        appendLine("import { ${runtimeImports.joinToString(", ")} } from \"./route-runtime.js\";")
      }
      appendLine("export type { ParsedRouteValues } from \"./route-runtime.js\";")
      appendLine()
      if (typescriptObjectEntries.any { it.queryString.isNotEmpty() }) {
        typescriptObjectEntries.filter { it.queryString.isNotEmpty() }.forEach { route ->
          appendLine("export enum ${route.key}QueryStringKey {")
          route.queryString.forEach { parameter ->
            appendLine("  ${parameter.name.queryStringKeyIdentifier()} = \"${parameter.name.toTypeScriptString()}\",")
          }
          appendLine("}")
          appendLine("export type ${route.key}QueryString = ${route.queryString.toTypeScriptParameterObject()};")
          appendLine()
        }
      }
      appendLine("export const $objectName = {")
      typescriptObjectEntries.forEach { route ->
        append(route.toTypeScriptObjectEntry())
      }
      appendLine("} as const;")
      appendLine()
      appendLine("export type $typeName = keyof typeof $objectName;")
      typescriptObjectEntries.forEach { route ->
        appendLine()
        appendLine("/** Parsed context for ${route.key}; use this type on preload parameters. */")
        appendLine("export type ${route.key}Context = NonNullable<ReturnType<typeof $objectName.${route.key}.parse>>;")
      }
    }
  }.toMap().toMutableMap()
  fileNameToContent["route-runtime"] = checkNotNull(
    TypeScriptRouteConfig::class.java.getResourceAsStream("route-runtime.ts")
  ) { "Missing bundled TypeScript route runtime: route-runtime.ts" }
    .bufferedReader(Charsets.UTF_8).use { it.readText() }

  if (Files.notExists(Path.of(outputDirectoryPath))) {
    Files.createDirectories(Path.of(outputDirectoryPath))
  }

  for ((fileName, content) in fileNameToContent) {
    val outputPath = Path.of("${outputDirectoryPath}/${fileName}.ts")
    Files.writeString(outputPath, content)
  }

  Files.list(Path.of(outputDirectoryPath)).use { stream ->
    stream
      .filter { Files.isRegularFile(it) }
      .filter { !fileNameToContent.keys.contains(it.fileName.toString().substringBeforeLast(".")) }
      .forEach {
        Files.delete(it)
      }
  }
}

private data class TypeScriptRouteConfig(
  val applicationId: String,
  val key: String,
  val path: String,
  val parameters: List<RouteParameter>,
  val queryString: List<RouteParameter>,
  val hasAccessHandler: Boolean
)

private fun TypeScriptRouteConfig.toTypeScriptObjectEntry(): String {
  val pathDeclarations = parameters.joinToString(", ") {
    "{ name: \"${it.name.toTypeScriptString()}\", optional: ${it.optional} }"
  }
  if (queryString.isNotEmpty()) {
    val arguments = buildList {
      if (parameters.isNotEmpty()) add("params: ${parameters.toTypeScriptParameterObject()}")
      val default = if (queryString.all { it.optional }) " = {}" else ""
      add("queryString: ${key}QueryString$default")
    }.joinToString(", ")
    val declarations = queryString.joinToString(", ") {
      "{ name: ${key}QueryStringKey.${it.name.queryStringKeyIdentifier()}, optional: ${it.optional}, repeated: ${it.repeated} }"
    }
    return buildString {
      appendLine("  $key: Object.assign(")
      appendLine("    ($arguments) => appendQueryString(${path.toTypeScriptTemplate(parameters)}, queryString, [$declarations]),")
      appendLine("    {")
      appendLine("      path: \"${path.toTypeScriptString()}\", ...${toTypeScriptRouteMetadata()},")
      appendLine("      queryString: (search: URLSearchParams) => readQueryString(search, Object.values(${key}QueryStringKey)),")
      val identity = "readonly [\"${applicationId.toTypeScriptString()}\", \"${key.toTypeScriptString()}\"]"
      appendLine("      parse: createRouteParser<${parameters.toTypeScriptParameterObject()}, ${key}QueryString, $identity>([$pathDeclarations], [$declarations])")
      appendLine("    }")
      appendLine("  ),")
    }
  }
  if (parameters.isEmpty()) {
    return "  $key: routeWithoutParams(\"${path.toTypeScriptString()}\", ${toTypeScriptRouteMetadata()}),\n"
  }

  return buildString {
    appendLine("  $key: route(")
    appendLine("    \"${path.toTypeScriptString()}\",")
    appendLine("    (params: ${parameters.toTypeScriptParameterObject()}) => ${path.toTypeScriptTemplate(parameters)},")
    appendLine("    ${toTypeScriptRouteMetadata()},")
    appendLine("    [$pathDeclarations]")
    appendLine("  ),")
  }
}

private fun TypeScriptRouteConfig.toTypeScriptRouteMetadata(): String {
  return "{ applicationId: \"${applicationId.toTypeScriptString()}\", routeId: \"${key.toTypeScriptString()}\", hasAccessHandler: $hasAccessHandler }"
}

private fun List<RouteParameter>.toTypeScriptParameterObject(): String {
  return joinToString(
    prefix = "{ ",
    separator = "; ",
    postfix = " }"
  ) { parameter ->
    val optionalMarker = if (parameter.optional) "?" else ""
    val type = if (parameter.repeated) "readonly string[]" else "string"
    "${parameter.name.toTypeScriptPropertyName()}$optionalMarker: $type"
  }
}

private fun String.toTypeScriptTemplate(parameters: List<RouteParameter>): String {
  val parametersBySegment = parameters.associateBy { ":${it.name}" }
  val template = split("/").joinToString("/") { segment ->
    val parameter = parametersBySegment[segment] ?: return@joinToString segment.toTypeScriptTemplateString()
    val propertyAccess = parameter.name.toTypeScriptPropertyAccess()
    if (parameter.optional) {
      "\${params$propertyAccess == null ? \"\" : encodeRouteParam(params$propertyAccess)}"
    } else {
      "\${encodeRouteParam(params$propertyAccess)}"
    }
  }
  return "`$template`"
}

private fun String.toTypeScriptPropertyName(): String {
  return if (isTypeScriptIdentifier()) {
    this
  } else {
    "\"${toTypeScriptString()}\""
  }
}

private fun String.toTypeScriptPropertyAccess(): String {
  return if (isTypeScriptIdentifier()) {
    ".$this"
  } else {
    "[\"${toTypeScriptString()}\"]"
  }
}

private fun String.isTypeScriptIdentifier(): Boolean {
  return Regex("[A-Za-z_$][A-Za-z0-9_$]*").matches(this)
}

private fun String.toTypeScriptString(): String {
  return buildString {
    this@toTypeScriptString.forEach { character ->
      append(when (character) {
        '\\' -> "\\\\"
        '"' -> "\\\""
        '\n' -> "\\n"
        '\r' -> "\\r"
        '\t' -> "\\t"
        else -> if (character < ' ') "\\u${character.code.toString(16).padStart(4, '0')}" else character.toString()
      })
    }
  }
}

private fun String.toTypeScriptTemplateString(): String {
  return replace("\\", "\\\\")
    .replace("`", "\\`")
    .replace("\$", "\\$")
}
