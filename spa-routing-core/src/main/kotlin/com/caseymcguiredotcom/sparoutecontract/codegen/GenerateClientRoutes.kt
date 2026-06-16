package com.caseymcguiredotcom.sparoutecontract.codegen

import com.caseymcguiredotcom.sparoutecontract.SpaApplicationDefinitionDiscovery
import com.caseymcguiredotcom.sparoutecontract.SpaRouteParameter
import com.caseymcguiredotcom.sparoutecontract.SpaRouteParameterType
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
        parameters = route.parameters
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
        appendLine("type RouteParamValue = string | number;")
        appendLine()
        appendLine("function encodeRouteParam(value: RouteParamValue): string {")
        appendLine("  return encodeURIComponent(String(value));")
        appendLine("}")
        appendLine()
        appendLine("function route<TParams extends object>(path: string, buildPath: (params: TParams) => string, ids: SpaRouteIds) {")
        appendLine("  return Object.assign(buildPath, { path, ...ids });")
        appendLine("}")
        appendLine()
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
  val parameters: List<SpaRouteParameter>
)

private fun TypeScriptRouteConfig.toTypeScriptObjectEntry(): String {
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
    "${parameter.name.toTypeScriptPropertyName()}$optionalMarker: ${parameter.toTypeScriptType()}"
  }
}

private fun SpaRouteParameter.toTypeScriptType(): String {
  return when (type) {
    SpaRouteParameterType.STRING -> "string"
    SpaRouteParameterType.INT -> "number"
    SpaRouteParameterType.UUID -> "string"
  }
}

private fun String.toTypeScriptTemplate(parameters: List<SpaRouteParameter>): String {
  var template = this.toTypeScriptTemplateString()
  parameters.forEach { parameter ->
    val propertyAccess = parameter.name.toTypeScriptPropertyAccess()
    val valueExpression = if (parameter.optional) {
      "\${params$propertyAccess == null ? \"\" : encodeRouteParam(params$propertyAccess)}"
    } else {
      "\${encodeRouteParam(params$propertyAccess)}"
    }
    template = template.replace(":${parameter.name}", valueExpression)
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
  return replace("\\", "\\\\").replace("\"", "\\\"")
}

private fun String.toTypeScriptTemplateString(): String {
  return replace("\\", "\\\\")
    .replace("`", "\\`")
    .replace("\$", "\\$")
}
