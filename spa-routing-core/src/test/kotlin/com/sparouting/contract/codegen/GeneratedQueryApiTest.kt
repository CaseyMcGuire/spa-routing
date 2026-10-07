package com.sparouting.contract.codegen

import com.sparouting.contract.AccessDecision
import com.sparouting.contract.SinglePageApplicationDefinitionDiscovery
import com.sparouting.contract.SinglePageApplicationConfig
import com.sparouting.contract.RouteRequest
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeneratedQueryApiTest {
  @Test
  fun `generated configs compile with only core and run without authored definitions`() = withGeneratedRoutes { output ->
    val sources = Files.walk(output.resolve("server")).use { files ->
      files.filter { it.toString().endsWith(".kt") }.toList()
    }
    val usage = output.resolve("ConfigUsage.kt")
    usage.writeText(configUsage())
    val classes = output.resolve("config-classes")
    val contractClasspath = listOf(SinglePageApplicationConfig::class.java, Unit::class.java)
      .map { Path.of(it.protectionDomain.codeSource.location.toURI()).toString() }
      .joinToString(java.io.File.pathSeparator)
    val (result, diagnostics) = compileKotlin(sources + listOf(usage), classes, baseClasspath = contractClasspath)
    assertEquals(ExitCode.OK, result, diagnostics)

    val parent = object : ClassLoader(javaClass.classLoader) {
      override fun loadClass(name: String, resolve: Boolean): Class<*> {
        if (name.startsWith("com.sparouting.contract.codegen.") ||
          name == "com.sparouting.contract.SinglePageApplicationDefinition" ||
          name == "com.sparouting.contract.RouteDefinition") {
          throw ClassNotFoundException(name)
        }
        return super.loadClass(name, resolve)
      }
    }
    URLClassLoader(arrayOf(classes.toUri().toURL()), parent).use { loader ->
      fun config(name: String): SinglePageApplicationConfig =
        loader.loadClass("ConfigUsageKt").getMethod("create$name").invoke(null) as SinglePageApplicationConfig

      val root = config("ConfigTestApplicationConfig")
      assertEquals("configtest", root.id)
      assertEquals("Config Test", root.name)
      assertEquals("custom-assets", root.bundleName)
      assertEquals("<h1>Config Test</h1>", root.htmlRenderer.render(root))
      assertEquals(AccessDecision.Allowed, root.applicationAccessHandler.evaluate(RouteRequest("configtest", "Post")))
      assertEquals(listOf("Post", "Class", "Handlers"), root.routeAccessHandlers.handlers.map { it.route.routeId })
      assertEquals("/", root.routes.single { it.id == "Index" }.path)
      val post = root.routes.single { it.id == "Post" }
      assertEquals("/posts/{postId}", post.path)
      assertTrue(post.hasAccessHandler)
      assertFalse(post.hasValidParameterValues(emptyMap()))
      assertTrue(post.hasValidParameterValues(mapOf("postId" to "42")))
      assertFalse(post.hasValidQueryStringValues(emptyMap()))
      assertEquals("tag=a+b&tag=%E9%9B%AA", post.resolveQueryString(mapOf("tag" to listOf("a b", "雪"))))
      assertEquals("/posts/42", post.resolvePath(mapOf("postId" to "42")))

      val access = config("AccessTestApplicationConfig")
      assertEquals("/access/posts/{postId}", access.routes.single { it.id == "Post" }.path)
      assertTrue(access.routes.single { it.id == "Optional" }.parameters.single().optional)
      assertFalse(access.routes.single { it.id == "Public" }.hasAccessHandler)
      assertTrue(access.routes.single { it.id == "Post" }.queryString.single { it.name == "filter" }.repeated)
      val special = config("QueryTestApplicationConfig").routes.single { it.id == "Special" }
      assertTrue(special.queryString.any { it.name == "a\"$\n" && it.optional })
      assertEquals("/collision", config("RouteApplicationConfig").routes.single().path)
      assertTrue(config("SinglePageApplicationConfig").routes.isEmpty())
    }

    val rendererArgument = "htmlRenderer = createConfigTestApplicationConfig().htmlRenderer"
    val failures = mapOf(
      "MissingApplicationHandler" to "ConfigTestApplicationConfig(routeAccessHandlers = createConfigTestApplicationConfig().routeAccessHandlers, $rendererArgument)",
      "MissingHandlerCollection" to "ConfigTestApplicationConfig(applicationAccessHandler = createConfigTestApplicationConfig().applicationAccessHandler, $rendererArgument)",
      "MissingHtmlRenderer" to "ConfigTestApplicationConfig(createConfigTestApplicationConfig().applicationAccessHandler, createConfigTestApplicationConfig().routeAccessHandlers)",
      "MissingRouteHandler" to "ConfigTestRouteAccessHandlers(`class` = CheckClass(), handlers = CheckHandlers())",
      "WrongRouteHandler" to "ConfigTestRouteAccessHandlers(post = CheckClass(), `class` = CheckClass(), handlers = CheckHandlers())",
      "WrongApplicationHandler" to "ConfigTestApplicationConfig(createRouteApplicationConfig().applicationAccessHandler, createConfigTestApplicationConfig().routeAccessHandlers, $rendererArgument)",
      "WrongHandlerCollection" to "ConfigTestApplicationConfig(createConfigTestApplicationConfig().applicationAccessHandler, RouteRouteAccessHandlers(), $rendererArgument)",
      "WrongGenericApplication" to "run { val handler: ApplicationAccessHandler<RouteApplicationConfig> = createConfigTestApplicationConfig().applicationAccessHandler }",
      "WrongGenericCollection" to "run { val handlers: RouteAccessHandlers<RouteApplicationConfig> = createConfigTestApplicationConfig().routeAccessHandlers }",
    ).map { (name, expression) ->
      output.resolve("$name.kt").also {
        it.writeText("""
          import generated.*
          import generated.configtest.*
          import com.sparouting.contract.AccessDecision
          import com.sparouting.contract.ApplicationAccessHandler
          import com.sparouting.contract.RouteAccessHandlers
          private class ${name}CheckClass : ClassAccessHandler() {
            override fun evaluate(request: ClassRequest): AccessDecision = AccessDecision.Allowed
          }
          private class ${name}CheckHandlers : HandlersAccessHandler() {
            override fun evaluate(request: HandlersRequest): AccessDecision = AccessDecision.Allowed
          }
          fun $name() { ${expression.replace("CheckClass", "${name}CheckClass").replace("CheckHandlers", "${name}CheckHandlers")} }
        """.trimIndent())
      }
    }
    val (failureResult, errors) = compileKotlin(failures, output.resolve("invalid-configs"), classes)
    assertEquals(ExitCode.COMPILATION_ERROR, failureResult, errors)
    failures.forEach { assertContains(errors, it.fileName.toString(), message = errors) }
  }

  private fun configUsage(): String = buildString {
    appendLine("import generated.*")
    appendLine("import com.sparouting.contract.AccessDecision")
    appendLine("import com.sparouting.contract.HtmlRenderer")
    appendLine("import com.sparouting.contract.RouteRequest")
    // Instantiate every fixture, including empty applications and type-name collisions.
    SinglePageApplicationDefinitionDiscovery.discoverFromSystemProperty().forEach { application ->
      val name = application.name.replace("\\s+".toRegex(), "")
      appendLine("fun create${name}ApplicationConfig(): ${name}ApplicationConfig = ${name}ApplicationConfig(")
      appendLine("  applicationAccessHandler = object : ${name}ApplicationAccessHandler() {")
      appendLine("    override fun evaluate(request: RouteRequest): AccessDecision = AccessDecision.Allowed")
      appendLine("  },")
      appendLine("  routeAccessHandlers = ${name}RouteAccessHandlers(")
      application.routes.filter { it.generateAccessHandler }.forEach { route ->
        val routePackage = "generated.${application.id}"
        appendLine("    object : $routePackage.${route.id}AccessHandler() {")
        appendLine("      override fun evaluate(request: $routePackage.${route.id}Request): AccessDecision = AccessDecision.Allowed")
        appendLine("    },")
      }
      appendLine("  ),")
      appendLine("  htmlRenderer = HtmlRenderer { application -> \"<h1>${'$'}{application.name}</h1>\" }")
      appendLine(")")
    }
  }

  @Test
  fun `generated access handlers compile decode typed requests and reject wrong request types`() = withGeneratedRoutes { output ->
    assertFalse(Files.exists(output.resolve("server/accesstest/PublicAccessHandler.kt")))
    assertFalse(Files.exists(output.resolve("server/accesstest/PublicRequest.kt")))
    val sources = Files.walk(output.resolve("server")).use { files ->
      files.filter { it.toString().endsWith(".kt") }.toList()
    }
    val classes = output.resolve("access-classes")
    val (result, diagnostics) = compileKotlin(
      sources + listOf(Path.of("src/test/fixtures/kotlin/AccessUsage.kt")), classes
    )
    assertEquals(ExitCode.OK, result, diagnostics)
    URLClassLoader(arrayOf(classes.toUri().toURL()), javaClass.classLoader).use { loader ->
      assertEquals("access verified", loader.loadClass("AccessUsageKt").getMethod("verifyAccess").invoke(null))
    }

    val invalid = output.resolve("WrongAccess.kt")
    invalid.writeText("""
      import com.sparouting.contract.AccessDecision
      import generated.accesstest.*
      class WrongAccess : PostAccessHandler() {
        override fun evaluate(request: StartRequest): AccessDecision = AccessDecision.Allowed
      }
    """.trimIndent())
    val (failureResult, errors) = compileKotlin(listOf(invalid), output.resolve("invalid-access"), classes)
    assertEquals(ExitCode.COMPILATION_ERROR, failureResult, errors)
    assertContains(errors, "WrongAccess")
  }

  @Test
  fun `generated Kotlin builders and enums compile and preserve query values`() = withGeneratedRoutes { output ->
    val sources = Files.walk(output.resolve("server")).use { files ->
      files.filter { it.toString().endsWith(".kt") }.toList()
    }
    val classes = output.resolve("classes")
    val (result, diagnostics) = compileKotlin(
      sources + listOf(Path.of("src/test/fixtures/kotlin/QueryUsage.kt")), classes
    )
    assertEquals(ExitCode.OK, result, diagnostics)
    URLClassLoader(arrayOf(classes.toUri().toURL()), javaClass.classLoader).use { loader ->
      val url = loader.loadClass("QueryUsageKt").getMethod("verifyQueries").invoke(null)
      assertEquals(EXPECTED_URL, url)
    }

    val failures = mapOf(
      "MissingQuery" to "UserDetail(id = \"123\")",
      "MissingRequiredKey" to "UserDetail.QueryString()",
      "UnknownKey" to "UserDetail.QueryString(foo = \"x\", unknown = \"x\")",
      "WrongScalar" to "UserDetail.QueryString(foo = listOf(\"x\"))",
      "WrongList" to "Filters.QueryString(tag = \"x\")",
      "MissingRequiredList" to "Filters.QueryString()",
      "NoArgumentBypass" to "UserDetail()",
      "QueryOnlyBypass" to "Search()",
      "WrongEnum" to "UserDetail.queryString(emptyMap())[Search.QueryStringKey.Q]",
      "UnknownEnum" to "UserDetail.QueryStringKey.UNKNOWN"
    ).map { (name, expression) ->
      output.resolve("$name.kt").also {
        it.writeText("import generated.querytest.*\nfun $name() { $expression }\n")
      }
    }
    val (failureResult, errors) = compileKotlin(failures, output.resolve("invalid-classes"), classes)
    assertEquals(ExitCode.COMPILATION_ERROR, failureResult, errors)
    failures.forEach { assertContains(errors, it.fileName.toString(), message = errors) }
  }

  @Test
  fun `generated TypeScript builders and enums type check and run`() = withGeneratedRoutes { output ->
    verifyTypeScript(output, "query-api", EXPECTED_URL)
  }

  @Test
  fun `generated route objects infer renderer arguments and parse incoming values`() = withGeneratedRoutes { output ->
    verifyTypeScript(output, "route-object-api", "route objects verified")
  }

  private fun verifyTypeScript(output: Path, fixture: String, expectedOutput: String) {
    val client = output.resolve("client")
    Files.copy(Path.of("src/test/typescript/$fixture.ts"), client.resolve("$fixture.ts"))
    val compiled = output.resolve("javascript")
    runProcess(listOf(
      "node", System.getProperty("test.typescript.compiler"),
      "--strict", "--noEmitOnError", "--target", "ES2020", "--module", "commonjs",
      "--outDir", compiled.toString(), client.resolve("$fixture.ts").toString()
    ))
    assertEquals(expectedOutput, runProcess(listOf("node", compiled.resolve("$fixture.js").toString())).trim())
  }

  private fun compileKotlin(
    sources: List<Path>,
    destination: Path,
    additionalClasspath: Path? = null,
    baseClasspath: String = System.getProperty("test.runtime.classpath")
  ): Pair<ExitCode, String> {
    val diagnostics = ByteArrayOutputStream()
    val classpath = listOfNotNull(baseClasspath, additionalClasspath?.toString())
      .joinToString(java.io.File.pathSeparator)
    val arguments = listOf("-no-stdlib", "-no-reflect", "-jvm-target", "21", "-classpath", classpath, "-d", destination.toString()) +
      sources.map { it.toString() }
    val result = PrintStream(diagnostics).use { K2JVMCompiler().exec(it, *arguments.toTypedArray()) }
    return result to diagnostics.toString(Charsets.UTF_8)
  }

  private fun runProcess(command: List<String>): String {
    val log = Files.createTempFile("spa-codegen-command", ".log")
    val process = ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start()
    if (!process.waitFor(60, TimeUnit.SECONDS)) {
      process.destroyForcibly()
      error("Timed out running ${command.first()}")
    }
    val output = log.readText()
    assertEquals(0, process.exitValue(), output)
    Files.delete(log)
    return output
  }

  private fun withGeneratedRoutes(block: (Path) -> Unit) {
    val output = Files.createTempDirectory("spa-query-api")
    val properties = listOf("spa.application.source.dir", "route.output.dir", "route.server.package")
      .associateWith { System.getProperty(it) }
    try {
      System.setProperty("spa.application.source.dir", "src/test/kotlin")
      System.setProperty("route.server.package", "generated")
      System.setProperty("route.output.dir", output.resolve("server").toString())
      generateServerRoutes()
      System.setProperty("route.output.dir", output.resolve("client").toString())
      generateClientRoutes()
      block(output)
    } finally {
      properties.forEach { (name, value) ->
        if (value == null) System.clearProperty(name) else System.setProperty(name, value)
      }
      output.toFile().deleteRecursively()
    }
  }

  companion object {
    private const val EXPECTED_URL = "/querytest/users/123?foo=a+b%2B%26%3D%E9%9B%AA&baz=&tag=x%2Fy&tag=%C3%A9"
  }
}
