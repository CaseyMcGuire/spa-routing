package com.sparouting.contract.codegen

import com.sparouting.contract.SpaApplicationDefinition
import com.sparouting.contract.SpaApplicationDefinitionDiscovery
import com.sparouting.contract.SpaRouteDefinition
import com.sparouting.contract.SpaRouteParameter
import com.sparouting.contract.queryStringKeyIdentifier
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
        if (route.generateAccessHandler) {
          Files.writeString(
            routeOutputDirectory.resolve("${route.id}AccessHandler.kt"),
            route.toKotlinAccessFile(application.routePackageName())
          )
          Files.writeString(
            routeOutputDirectory.resolve("${route.id}Request.kt"),
            route.toKotlinRequestFile(application.routePackageName())
          )
        }
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
    var queryArgument = "queryString"
    val pathIdentifiers = parameters.map { it.name.routeParameterIdentifier() }.toSet()
    while (queryArgument in pathIdentifiers) queryArgument += "_"
    val arguments = parameters.map { it.toKotlinParameter() }.toMutableList()
    if (queryString.isNotEmpty()) {
      val default = if (queryString.all { it.optional }) " = QueryString()" else ""
      arguments.add("$queryArgument: QueryString$default")
    }
    appendLine("  operator fun invoke(${arguments.joinToString(", ")}): SpaRouteTarget {")
    val queryValues = if (queryString.isEmpty()) "" else ", queryString = $queryArgument.toQueryStringValues()"
    appendLine("    return target(${parameters.toKotlinParameterMap()}$queryValues)")
    appendLine("  }")
    if (queryString.isNotEmpty()) {
      appendQueryStringModel(queryString)
    }
    appendLine("}")
  }
}

private fun SpaRouteDefinition.requestPropertyNames(): Pair<String, String> {
  val used = parameters.map { it.name.routeParameterIdentifier() }.toMutableSet()
  fun available(name: String): String {
    var candidate = name
    while (!used.add(candidate)) {
      candidate += "_"
    }
    return candidate
  }
  return available("queryString") to available("context")
}

private fun SpaRouteDefinition.toKotlinRequestFile(packageName: String): String {
  val (queryProperty, contextProperty) = requestPropertyNames()
  return buildString {
    appendGeneratedFileHeader(packageName, listOf("com.sparouting.contract.SpaRouteAccessContext"))
    appendLine("/** Typed input for $id access, with raw request metadata in [$contextProperty]. */")
    appendLine("data class ${id}Request(")
    parameters.forEach { parameter ->
      appendLine("  val ${parameter.toKotlinParameter()},")
    }
    if (queryString.isNotEmpty()) {
      appendLine("  val $queryProperty: $id.QueryString,")
    }
    appendLine("  val $contextProperty: SpaRouteAccessContext")
    appendLine(")")
  }
}

private fun SpaRouteDefinition.toKotlinAccessFile(packageName: String): String {
  val (queryProperty, contextProperty) = requestPropertyNames()
  return buildString {
    appendGeneratedFileHeader(
      packageName,
      listOf("com.sparouting.contract.RouteAccessHandler", "com.sparouting.contract.SpaRouteAccessContext")
    )
    appendLine("/** Implement evaluate and register the implementation with your server's DI container. */")
    appendLine("abstract class ${id}AccessHandler : RouteAccessHandler<${id}Request>($id) {")
    appendLine("  final override fun createRequest(context: SpaRouteAccessContext): ${id}Request {")
    appendLine("    return ${id}Request(")
    parameters.forEach { parameter ->
      val key = parameter.name.toKotlinStringLiteral()
      val value = if (parameter.optional) "context.pathParameters[$key]" else "context.pathParameters.getValue($key)"
      appendLine("      ${parameter.name.toKotlinIdentifier()} = $value,")
    }
    if (queryString.isNotEmpty()) {
      appendLine("      $queryProperty = $id.QueryString(")
      queryString.forEach { parameter ->
        val key = parameter.name.toKotlinStringLiteral()
        val values = if (parameter.optional) "context.queryString[$key]?" else "context.queryString.getValue($key)"
        val conversion = when {
          parameter.repeated -> "toList()"
          parameter.optional -> "singleOrNull()"
          else -> "single()"
        }
        appendLine("        ${parameter.name.toKotlinIdentifier()} = $values.$conversion,")
      }
      appendLine("      ),")
    }
    appendLine("      $contextProperty = context")
    appendLine("    )")
    appendLine("  }")
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

private fun StringBuilder.appendQueryStringModel(parameters: List<SpaRouteParameter>) {
  appendLine()
  appendLine("  data class QueryString(")
  parameters.forEachIndexed { index, parameter ->
    val comma = if (index == parameters.lastIndex) "" else ","
    appendLine("    val ${parameter.toKotlinParameter()}$comma")
  }
  appendLine("  ) {")
  appendLine("    internal fun toQueryStringValues(): Map<String, List<String>> {")
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
  appendLine("  enum class QueryStringKey(val wireName: String) {")
  parameters.forEachIndexed { index, parameter ->
    val separator = if (index == parameters.lastIndex) "" else ","
    appendLine("    ${parameter.name.queryStringKeyIdentifier()}(${parameter.name.toKotlinStringLiteral()})$separator")
  }
  appendLine("  }")
  appendLine()
  appendLine("  fun queryString(values: Map<String, List<String>>): Map<QueryStringKey, List<String>> {")
  appendLine("    return buildMap {")
  appendLine("      QueryStringKey.entries.forEach { key ->")
  appendLine("        values[key.wireName]?.let { put(key, it.toList()) }")
  appendLine("      }")
  appendLine("    }")
  appendLine("  }")
}
