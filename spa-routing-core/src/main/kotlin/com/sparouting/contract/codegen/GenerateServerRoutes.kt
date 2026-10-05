package com.sparouting.contract.codegen

import com.sparouting.contract.ApplicationAccessHandler
import com.sparouting.contract.HtmlRenderer
import com.sparouting.contract.RouteAccessHandler
import com.sparouting.contract.RouteAccessHandlers
import com.sparouting.contract.SinglePageApplicationConfig
import com.sparouting.contract.SinglePageApplicationDefinition
import com.sparouting.contract.SinglePageApplicationDefinitionDiscovery
import com.sparouting.contract.RouteDefinition
import com.sparouting.contract.RouteParameter
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

  SinglePageApplicationDefinitionDiscovery.discoverFromSystemProperty()
    .sortedBy { it.name }
    .forEach { application ->
      Files.writeString(
        outputDirectory.resolve("${application.routesObjectName()}.kt"),
        application.toKotlinRoutesObjectFile()
      )
      Files.writeString(
        outputDirectory.resolve("${application.configClassName()}.kt"),
        application.toKotlinConfigFile()
      )
      Files.writeString(
        outputDirectory.resolve("${application.applicationAccessHandlerClassName()}.kt"),
        application.toKotlinApplicationAccessHandlerFile()
      )
      Files.writeString(
        outputDirectory.resolve("${application.routeAccessHandlersClassName()}.kt"),
        application.toKotlinRouteAccessHandlersFile()
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

private fun SinglePageApplicationDefinition.toKotlinRoutesObjectFile(): String {
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

private fun SinglePageApplicationDefinition.configClassName(): String = "${name.withoutWhitespace()}ApplicationConfig"

private fun SinglePageApplicationDefinition.applicationAccessHandlerClassName(): String =
  "${name.withoutWhitespace()}ApplicationAccessHandler"

private fun SinglePageApplicationDefinition.routeAccessHandlersClassName(): String =
  "${name.withoutWhitespace()}RouteAccessHandlers"

private fun SinglePageApplicationDefinition.toKotlinConfigFile(): String {
  val configInterface = SinglePageApplicationConfig::class.java
  val rendererInterface = HtmlRenderer::class.java
  val shadowsInterface = configClassName() == configInterface.simpleName
  return buildString {
    appendGeneratedFileHeader(
      generatedPackage(),
      listOf("com.sparouting.contract.RouteManifest", "com.sparouting.contract.RouteParameter", rendererInterface.name) +
        if (shadowsInterface) emptyList() else listOf(configInterface.name)
    )
    appendLine("class ${configClassName()}(")
    appendLine("  override val applicationAccessHandler: ${applicationAccessHandlerClassName()},")
    appendLine("  override val routeAccessHandlers: ${routeAccessHandlersClassName()},")
    appendLine("  override val htmlRenderer: ${rendererInterface.simpleName}")
    appendLine(") : ${if (shadowsInterface) configInterface.name else configInterface.simpleName} {")
    appendLine("  override val id: String = ${id.toKotlinStringLiteral()}")
    appendLine("  override val name: String = ${name.toKotlinStringLiteral()}")
    appendLine("  override val bundleName: String = ${bundleName.toKotlinStringLiteral()}")
    appendLine("  override val routes: List<RouteManifest> = listOf(")
    routes.forEach { route ->
      appendLine("    RouteManifest(")
      appendLine("      path = ${getFullPathPattern(route).toKotlinStringLiteral()},")
      appendLine("      id = ${route.id.toKotlinStringLiteral()},")
      appendLine("      parameters = ${route.parameters.toKotlinManifestParameters()},")
      appendLine("      queryString = ${route.queryString.toKotlinManifestParameters()},")
      appendLine("      hasAccessHandler = ${route.generateAccessHandler}")
      appendLine("    ),")
    }
    appendLine("  )")
    appendLine("}")
  }
}

private fun SinglePageApplicationDefinition.toKotlinApplicationAccessHandlerFile(): String = buildString {
  val handlerType = ApplicationAccessHandler::class.java
  appendGeneratedFileHeader(generatedPackage(), listOf(handlerType.name))
  appendLine("abstract class ${applicationAccessHandlerClassName()} : ${handlerType.simpleName}<${configClassName()}>()")
}

private fun SinglePageApplicationDefinition.toKotlinRouteAccessHandlersFile(): String {
  val gatedRoutes = routes.filter { it.generateAccessHandler }
  val handlerType = RouteAccessHandler::class.java
  val collectionType = RouteAccessHandlers::class.java
  val shadowsHandler = gatedRoutes.any { "${it.id}AccessHandler" == handlerType.simpleName }
  return buildString {
    appendGeneratedFileHeader(
      generatedPackage(),
      listOf(collectionType.name) +
        gatedRoutes.map { "${routePackageName()}.${it.id}AccessHandler" } +
        if (shadowsHandler) emptyList() else listOf(handlerType.name)
    )
    appendLine("class ${routeAccessHandlersClassName()}(")
    gatedRoutes.forEach { route ->
      appendLine("  ${route.handlerParameterName()}: ${route.id}AccessHandler,")
    }
    appendLine(") : ${collectionType.simpleName}<${configClassName()}> {")
    appendLine("  override val handlers: List<${if (shadowsHandler) handlerType.name else handlerType.simpleName}<*>> =")
    appendLine("    listOf(${gatedRoutes.joinToString(", ") { it.handlerParameterName() }})")
    appendLine("}")
  }
}

private fun RouteDefinition.handlerParameterName(): String = id.replaceFirstChar { it.lowercaseChar() }.toKotlinIdentifier()

private fun List<RouteParameter>.toKotlinManifestParameters(): String {
  return joinToString(prefix = "listOf(", postfix = ")") { parameter ->
    "RouteParameter(${parameter.name.toKotlinStringLiteral()}, optional = ${parameter.optional}, repeated = ${parameter.repeated})"
  }
}

private fun RouteDefinition.toKotlinRouteObjectFile(
  applicationId: String,
  packageName: String
): String {
  return buildString {
    appendGeneratedFileHeader(
      packageName = packageName,
      imports = listOf(
        "com.sparouting.contract.RouteTarget",
        "com.sparouting.contract.Route"
      )
    )

    appendLine("object $id : Route(${applicationId.toKotlinStringLiteral()}, ${id.toKotlinStringLiteral()}) {")
    var queryArgument = "queryString"
    val pathIdentifiers = parameters.map { it.name.routeParameterIdentifier() }.toSet()
    while (queryArgument in pathIdentifiers) queryArgument += "_"
    val arguments = parameters.map { it.toKotlinParameter() }.toMutableList()
    if (queryString.isNotEmpty()) {
      val default = if (queryString.all { it.optional }) " = QueryString()" else ""
      arguments.add("$queryArgument: QueryString$default")
    }
    appendLine("  operator fun invoke(${arguments.joinToString(", ")}): RouteTarget {")
    val queryValues = if (queryString.isEmpty()) "" else ", queryString = $queryArgument.toQueryStringValues()"
    appendLine("    return target(${parameters.toKotlinParameterMap()}$queryValues)")
    appendLine("  }")
    if (queryString.isNotEmpty()) {
      appendQueryStringModel(queryString)
    }
    appendLine("}")
  }
}

private fun RouteDefinition.requestPropertyNames(): Pair<String, String> {
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

private fun RouteDefinition.toKotlinRequestFile(packageName: String): String {
  val (queryProperty, contextProperty) = requestPropertyNames()
  return buildString {
    appendGeneratedFileHeader(packageName, listOf("com.sparouting.contract.RouteAccessContext"))
    appendLine("/** Typed input for $id access, with target route context and caller headers in [$contextProperty]. */")
    appendLine("data class ${id}Request(")
    parameters.forEach { parameter ->
      appendLine("  val ${parameter.toKotlinParameter()},")
    }
    if (queryString.isNotEmpty()) {
      appendLine("  val $queryProperty: $id.QueryString,")
    }
    appendLine("  val $contextProperty: RouteAccessContext")
    appendLine(")")
  }
}

private fun RouteDefinition.toKotlinAccessFile(packageName: String): String {
  val (queryProperty, contextProperty) = requestPropertyNames()
  val routeReference = when (id) {
    "RouteAccessHandler", "RouteAccessContext" -> "$packageName.$id"
    else -> id
  }
  return buildString {
    appendGeneratedFileHeader(
      packageName,
      listOf("com.sparouting.contract.RouteAccessHandler", "com.sparouting.contract.RouteAccessContext")
    )
    appendLine("/** Implement evaluate and pass the implementation to the generated route handler collection. */")
    appendLine("abstract class ${id}AccessHandler : RouteAccessHandler<${id}Request>($routeReference) {")
    appendLine("  final override fun createRequest(context: RouteAccessContext): ${id}Request {")
    appendLine("    return ${id}Request(")
    parameters.forEach { parameter ->
      val key = parameter.name.toKotlinStringLiteral()
      val value = if (parameter.optional) "context.pathParameters[$key]" else "context.pathParameters.getValue($key)"
      appendLine("      ${parameter.name.toKotlinIdentifier()} = $value,")
    }
    if (queryString.isNotEmpty()) {
      appendLine("      $queryProperty = $routeReference.QueryString(")
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
  appendLine("// Run './gradlew generateServerRoutes' to regenerate.")
  appendLine()
}

private fun SinglePageApplicationDefinition.routesObjectName(): String {
  return "${name.withoutWhitespace()}Routes"
}

private fun SinglePageApplicationDefinition.routePackageName(): String {
  return "${generatedPackage()}.${routePackagePath()}"
}

private fun SinglePageApplicationDefinition.routePackagePath(): String {
  return id.toPackageSegment()
}

private fun RouteParameter.toKotlinParameter(): String {
  val nullableSuffix = if (optional) "?" else ""
  val defaultValue = if (optional) " = null" else ""
  val type = if (repeated) "List<String>" else "String"
  return "${name.toKotlinIdentifier()}: $type$nullableSuffix$defaultValue"
}

private fun List<RouteParameter>.toKotlinParameterMap(): String {
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

private fun StringBuilder.appendQueryStringModel(parameters: List<RouteParameter>) {
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
