package com.sparouting.contract.codegen

import com.sparouting.contract.SpaApplicationDefinitionDiscovery
import com.sparouting.contract.SpaRouteParameter
import com.sparouting.contract.queryKeyIdentifier
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
  val configs = SpaApplicationDefinitionDiscovery.discoverFromSystemProperty()
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
        path = routeConverter.convertToReactRouter(config.getFullUrl(route.path)),
        parameters = route.parameters,
        queryParameters = route.queryParameters
      )
    }
  }

  val fileNameToContent = configNameToTypeScriptObjectEntries.map { entry ->
    val typeName = "${entry.key}Route"
    val objectName = "${entry.key}Routes"
    val typescriptObjectEntries = entry.value.sortedBy { it.key }

    objectName to buildString {
      appendLine("// THIS FILE IS GENERATED. DO NOT EDIT BY HAND.")
      appendLine("// Run './gradlew generateClientRoutes' to regenerate.")
      appendLine()
      appendLine("type SpaRouteIds = { applicationId: string; routeId: string };")
      appendLine()
      appendLine("function routeWithoutParams(path: string, ids: SpaRouteIds) {")
      appendLine("  return Object.assign(() => path, { path, ...ids });")
      appendLine("}")
      appendLine()
      if (typescriptObjectEntries.any { it.parameters.isNotEmpty() }) {
        appendLine("function encodeRouteParam(value: string): string {")
        appendLine("  return encodeURIComponent(value);")
        appendLine("}")
        appendLine()
        appendLine("function route<TParams extends object>(path: string, buildPath: (params: TParams) => string, ids: SpaRouteIds) {")
        appendLine("  return Object.assign(buildPath, { path, ...ids });")
        appendLine("}")
        appendLine()
      }
      if (typescriptObjectEntries.any { it.queryParameters.isNotEmpty() }) {
        appendLine(QUERY_HELPERS)
        typescriptObjectEntries.filter { it.queryParameters.isNotEmpty() }.forEach { route ->
          appendLine("export enum ${route.key}QueryKey {")
          route.queryParameters.forEach { parameter ->
            appendLine("  ${parameter.name.queryKeyIdentifier()} = \"${parameter.name.toTypeScriptString()}\",")
          }
          appendLine("}")
          appendLine("export type ${route.key}Query = ${route.queryParameters.toTypeScriptParameterObject()};")
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
    }
  }.toMap()

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
  val parameters: List<SpaRouteParameter>,
  val queryParameters: List<SpaRouteParameter>
)

private fun TypeScriptRouteConfig.toTypeScriptObjectEntry(): String {
  if (queryParameters.isNotEmpty()) {
    val arguments = buildList {
      if (parameters.isNotEmpty()) add("params: ${parameters.toTypeScriptParameterObject()}")
      val default = if (queryParameters.all { it.optional }) " = {}" else ""
      add("query: ${key}Query$default")
    }.joinToString(", ")
    val declarations = queryParameters.joinToString(", ") {
      "{ name: ${key}QueryKey.${it.name.queryKeyIdentifier()}, optional: ${it.optional}, repeated: ${it.repeated} }"
    }
    return buildString {
      appendLine("  $key: Object.assign(")
      appendLine("    ($arguments) => appendQuery(${path.toTypeScriptTemplate(parameters)}, query, [$declarations]),")
      appendLine("    {")
      appendLine("      path: \"${path.toTypeScriptString()}\", ...${toTypeScriptRouteIds()},")
      appendLine("      queryParameters: (search: URLSearchParams) => readQuery(search, Object.values(${key}QueryKey))")
      appendLine("    }")
      appendLine("  ),")
    }
  }
  if (parameters.isEmpty()) {
    return "  $key: routeWithoutParams(\"${path.toTypeScriptString()}\", ${toTypeScriptRouteIds()}),\n"
  }

  return buildString {
    appendLine("  $key: route(")
    appendLine("    \"${path.toTypeScriptString()}\",")
    appendLine("    (params: ${parameters.toTypeScriptParameterObject()}) => ${path.toTypeScriptTemplate(parameters)},")
    appendLine("    ${toTypeScriptRouteIds()}")
    appendLine("  ),")
  }
}

private fun TypeScriptRouteConfig.toTypeScriptRouteIds(): String {
  return "{ applicationId: \"${applicationId.toTypeScriptString()}\", routeId: \"${key.toTypeScriptString()}\" }"
}

private fun List<SpaRouteParameter>.toTypeScriptParameterObject(): String {
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

private fun String.toTypeScriptTemplate(parameters: List<SpaRouteParameter>): String {
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

private val QUERY_HELPERS = """
  type QueryDeclaration = { name: string; optional: boolean; repeated: boolean };

  function appendQuery(path: string, query: object, declarations: readonly QueryDeclaration[]): string {
    const values = query as Record<string, string | readonly string[] | null | undefined>;
    const search = new URLSearchParams();
    for (const { name, optional, repeated } of declarations) {
      const value = values != null && Object.prototype.hasOwnProperty.call(values, name) ? values[name] : undefined;
      if (value == null) {
        if (!optional) throw new Error("Missing required query parameter: " + name);
        continue;
      }
      if (repeated) {
        if (!Array.isArray(value)) throw new Error("Expected a list for query parameter: " + name);
        if (!optional && value.length === 0) throw new Error("Required query parameter must contain a value: " + name);
        for (const item of value) {
          if (typeof item !== "string") throw new Error("Expected string query values: " + name);
          search.append(name, item);
        }
      } else {
        if (typeof value !== "string") throw new Error("Expected a string for query parameter: " + name);
        search.append(name, value);
      }
    }
    const encoded = search.toString();
    return encoded ? path + "?" + encoded : path;
  }

  function readQuery<TKey extends string>(search: URLSearchParams, keys: readonly TKey[]): Readonly<Partial<Record<TKey, readonly string[]>>> {
    const values = Object.create(null) as Partial<Record<TKey, readonly string[]>>;
    for (const key of keys) {
      if (search.has(key)) values[key] = Object.freeze(search.getAll(key));
    }
    return Object.freeze(values);
  }

""".trimIndent()
