package com.sparouting.contract.codegen

import com.sparouting.contract.SpaApplicationDefinition
import com.sparouting.contract.SpaApplicationDefinitionDiscovery
import com.sparouting.contract.SpaRouteDefinition
import com.sparouting.contract.SpaRouteParameter
import com.sparouting.contract.queryKeyIdentifier
import com.sparouting.contract.routeParameterIdentifier
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlin.io.path.createDirectories

private const val DEFAULT_GENERATED_PACKAGE = "com.sparouting.generated.spa.routes"

private fun generatedPackage(): String {
  return System.getProperty("route.server.package") ?: DEFAULT_GENERATED_PACKAGE
}

fun main() {
  generateServerRoutes()
}

internal fun generateServerRoutes() {
  val outputDirectoryPath = System.getProperty("route.output.dir")
    ?: throw IllegalArgumentException("'route.output.dir' must be set in task config")
  val outputDirectory = Path.of(outputDirectoryPath)
  outputDirectory.createDirectories()
  cleanGeneratedFiles(outputDirectory)

  SpaApplicationDefinitionDiscovery.discoverFromSystemProperty()
    .sortedBy { it.name }
    .forEach { application ->
      Files.writeString(
        outputDirectory.resolve("${application.routesObjectName()}.kt"),
        application.toKotlinRoutesObjectFile()
      )

      val routeOutputDirectory = outputDirectory.resolve(application.routePackagePath())
      routeOutputDirectory.createDirectories()
      application.routes.forEach { route ->
        Files.writeString(
          routeOutputDirectory.resolve("${route.id}.kt"),
          route.toKotlinRouteObjectFile(application.id, application.routePackageName())
        )
      }
    }
}

private fun cleanGeneratedFiles(outputDirectory: Path) {
  Files.walk(outputDirectory).use { paths ->
    paths
      .sorted(Comparator.reverseOrder())
      .filter { it != outputDirectory }
      .forEach { path ->
        when {
          Files.isRegularFile(path) && path.fileName.toString().endsWith(".kt") -> Files.delete(path)
          Files.isDirectory(path) && path.isEmptyDirectory() -> Files.delete(path)
        }
      }
  }
}

private fun Path.isEmptyDirectory(): Boolean {
  return Files.list(this).use { files ->
    files.findAny().isEmpty
  }
}

private fun SpaApplicationDefinition.toKotlinRoutesObjectFile(): String {
  return buildString {
    appendGeneratedFileHeader(
      packageName = generatedPackage(),
      imports = routes.map { route ->
        "${routePackageName()}.${route.id} as ${route.id}Route"
      }
    )
    appendLine("object ${routesObjectName()} {")
    routes.forEach { route ->
      appendLine("  val ${route.id} = ${route.id}Route")
    }
    appendLine("}")
  }
}

private fun SpaRouteDefinition.toKotlinRouteObjectFile(
  applicationId: String,
  packageName: String
): String {
  return buildString {
    appendGeneratedFileHeader(
      packageName = packageName,
      imports = listOf(
        "com.sparouting.contract.SpaRouteTarget",
        "com.sparouting.contract.SpaTypedRoute"
      )
    )

    appendLine("object $id : SpaTypedRoute(${applicationId.toKotlinStringLiteral()}, ${id.toKotlinStringLiteral()}) {")
    var queryArgument = "query"
    val pathIdentifiers = parameters.map { it.name.routeParameterIdentifier() }.toSet()
    while (queryArgument in pathIdentifiers) queryArgument += "_"
    val arguments = parameters.map { it.toKotlinParameter() }.toMutableList()
    if (queryParameters.isNotEmpty()) {
      val default = if (queryParameters.all { it.optional }) " = Query()" else ""
      arguments.add("$queryArgument: Query$default")
    }
    appendLine("  operator fun invoke(${arguments.joinToString(", ")}): SpaRouteTarget {")
    val queryValues = if (queryParameters.isEmpty()) "" else ", queryParameters = $queryArgument.toQueryParameters()"
    appendLine("    return target(${parameters.toKotlinParameterMap()}$queryValues)")
    appendLine("  }")
    if (queryParameters.isNotEmpty()) {
      appendQueryModel(queryParameters)
    }
    appendLine("}")
  }
}

private fun StringBuilder.appendGeneratedFileHeader(
  packageName: String,
  imports: List<String> = emptyList()
) {
  appendLine("package $packageName")
  appendLine()
  imports.forEach { import ->
    appendLine("import $import")
  }
  if (imports.isNotEmpty()) {
    appendLine()
  }
  appendLine("// THIS FILE IS GENERATED. DO NOT EDIT BY HAND.")
  appendLine("// Run './gradlew generateServerSpaRoutes' to regenerate.")
  appendLine()
}

private fun SpaApplicationDefinition.routesObjectName(): String {
  return "${name.withoutWhitespace()}Routes"
}

private fun SpaApplicationDefinition.routePackageName(): String {
  return "${generatedPackage()}.${routePackagePath()}"
}

private fun SpaApplicationDefinition.routePackagePath(): String {
  return id.toPackageSegment()
}

private fun SpaRouteParameter.toKotlinParameter(): String {
  val nullableSuffix = if (optional) "?" else ""
  val defaultValue = if (optional) " = null" else ""
  val type = if (repeated) "List<String>" else "String"
  return "${name.toKotlinIdentifier()}: $type$nullableSuffix$defaultValue"
}

private fun List<SpaRouteParameter>.toKotlinParameterMap(): String {
  if (all { !it.optional }) {
    return "mapOf(${joinToString(", ") { "${it.name.toKotlinStringLiteral()} to ${it.name.toKotlinIdentifier()}" }})"
  }

  return buildString {
    appendLine("buildMap {")
    this@toKotlinParameterMap.forEach { parameter ->
      val identifier = parameter.name.toKotlinIdentifier()
      if (parameter.optional) {
        appendLine("        if ($identifier != null) {")
        appendLine("          put(${parameter.name.toKotlinStringLiteral()}, $identifier)")
        appendLine("        }")
      } else {
        appendLine("        put(${parameter.name.toKotlinStringLiteral()}, $identifier)")
      }
    }
    append("      }")
  }
}

private fun String.withoutWhitespace(): String {
  return replace("\\s+".toRegex(), "")
}

private fun String.toPackageSegment(): String {
  val sanitized = lowercase().replace("[^a-z0-9_]".toRegex(), "_")
  return if (sanitized.firstOrNull()?.isDigit() == true) {
    "_$sanitized"
  } else {
    sanitized
  }
}

private fun StringBuilder.appendQueryModel(parameters: List<SpaRouteParameter>) {
  appendLine()
  appendLine("  data class Query(")
  parameters.forEachIndexed { index, parameter ->
    val comma = if (index == parameters.lastIndex) "" else ","
    appendLine("    val ${parameter.toKotlinParameter()}$comma")
  }
  appendLine("  ) {")
  appendLine("    internal fun toQueryParameters(): Map<String, List<String>> {")
  parameters.filter { it.repeated && !it.optional }.forEach { parameter ->
    appendLine("      require(this.${parameter.name.toKotlinIdentifier()}.isNotEmpty()) {")
    appendLine("        ${("Required query parameter ${parameter.name} must contain at least one value.").toKotlinStringLiteral()}")
    appendLine("      }")
  }
  appendLine("      val queryValues = this")
  appendLine("      return buildMap {")
  parameters.forEach { parameter ->
    val property = "queryValues.${parameter.name.toKotlinIdentifier()}"
    val key = parameter.name.toKotlinStringLiteral()
    if (parameter.optional) {
      appendLine("        $property?.let { value ->")
      if (parameter.repeated) {
        appendLine("          if (value.isNotEmpty()) put($key, value.toList())")
      } else {
        appendLine("          put($key, listOf(value))")
      }
      appendLine("        }")
    } else {
      val values = if (parameter.repeated) "$property.toList()" else "listOf($property)"
      appendLine("        put($key, $values)")
    }
  }
  appendLine("      }")
  appendLine("    }")
  appendLine("  }")
  appendLine()
  appendLine("  enum class QueryKey(val wireName: String) {")
  parameters.forEachIndexed { index, parameter ->
    val separator = if (index == parameters.lastIndex) "" else ","
    appendLine("    ${parameter.name.queryKeyIdentifier()}(${parameter.name.toKotlinStringLiteral()})$separator")
  }
  appendLine("  }")
  appendLine()
  appendLine("  fun queryParameters(values: Map<String, List<String>>): Map<QueryKey, List<String>> {")
  appendLine("    return buildMap {")
  appendLine("      QueryKey.entries.forEach { key ->")
  appendLine("        values[key.wireName]?.let { put(key, it.toList()) }")
  appendLine("      }")
  appendLine("    }")
  appendLine("  }")
}
