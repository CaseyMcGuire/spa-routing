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

    objectName to buildString {
      appendLine("// THIS FILE IS GENERATED. DO NOT EDIT BY HAND.")
      appendLine("// Run './gradlew generateClientRoutes' to regenerate.")
      appendLine()
      appendLine("type RouteMetadata = { applicationId: string; routeId: string; hasAccessHandler: boolean };")
      appendLine()
      appendLine(ROUTE_PARSER)
      appendLine("function routeWithoutParams(path: string, metadata: RouteMetadata) {")
      appendLine("  return Object.assign(() => path, { path, ...metadata, parse: createRouteParser<{}, {}>([], []) });")
      appendLine("}")
      appendLine()
      if (typescriptObjectEntries.any { it.parameters.isNotEmpty() }) {
        appendLine("function encodeRouteParam(value: string): string {")
        appendLine("  return encodeURIComponent(value);")
        appendLine("}")
        appendLine()
        appendLine("function route<TParams extends object>(path: string, buildPath: (params: TParams) => string, metadata: RouteMetadata, parameters: readonly PathDeclaration[]) {")
        appendLine("  return Object.assign(buildPath, { path, ...metadata, parse: createRouteParser<TParams, {}>(parameters, []) });")
        appendLine("}")
        appendLine()
      }
      if (typescriptObjectEntries.any { it.queryString.isNotEmpty() }) {
        appendLine(QUERY_STRING_HELPERS)
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
      appendLine("      parse: createRouteParser<${parameters.toTypeScriptParameterObject()}, ${key}QueryString>([$pathDeclarations], [$declarations])")
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

private val ROUTE_PARSER = """
  type PathDeclaration = { name: string; optional: boolean };
  type QueryStringDeclaration = { name: string; optional: boolean; repeated: boolean };

  function createRouteParser<TParams extends object, TQueryString extends object>(
    parameters: readonly PathDeclaration[], queryStringDeclarations: readonly QueryStringDeclaration[]
  ) {
    // Path values have already been decoded by the router. Invalid declared values return null.
    return (params: Readonly<Record<string, string | undefined>>, search: URLSearchParams):
      { params: TParams; queryString: TQueryString } | null => {
      const parsedParams: Record<string, string> = Object.create(null);
      const queryString: Record<string, string | readonly string[]> = Object.create(null);
      for (const { name, optional } of parameters) {
        const value = Object.prototype.hasOwnProperty.call(params, name) ? params[name] : undefined;
        if (value === undefined) {
          if (!optional) {
            return null;
          }
        } else {
          if (typeof value !== "string") {
            return null;
          }
          parsedParams[name] = value;
        }
      }
      for (const { name, optional, repeated } of queryStringDeclarations) {
        const values = search.getAll(name);
        if (values.length === 0) {
          if (!optional) {
            return null;
          }
        } else {
          if (!repeated && values.length !== 1) {
            return null;
          }
          queryString[name] = repeated ? values : values[0];
        }
      }
      // The generator supplies matching declarations and types for each route.
      return { params: parsedParams as TParams, queryString: queryString as TQueryString };
    };
  }

""".trimIndent()

private val QUERY_STRING_HELPERS = """
  function appendQueryString(path: string, queryString: object, declarations: readonly QueryStringDeclaration[]): string {
    const values = queryString as Record<string, string | readonly string[] | null | undefined>;
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

  function readQueryString<TKey extends string>(search: URLSearchParams, keys: readonly TKey[]): Readonly<Partial<Record<TKey, readonly string[]>>> {
    const values = Object.create(null) as Partial<Record<TKey, readonly string[]>>;
    for (const key of keys) {
      if (search.has(key)) values[key] = Object.freeze(search.getAll(key));
    }
    return Object.freeze(values);
  }

""".trimIndent()
