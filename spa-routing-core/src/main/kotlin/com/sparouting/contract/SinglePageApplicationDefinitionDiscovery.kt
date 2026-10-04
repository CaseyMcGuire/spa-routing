package com.sparouting.contract

import java.nio.file.Files
import java.nio.file.Path

object SinglePageApplicationDefinitionDiscovery {
  fun discover(sourceDirectory: Path): List<SinglePageApplicationDefinition> {
    require(Files.isDirectory(sourceDirectory)) {
      "SPA application source directory does not exist: $sourceDirectory"
    }

    val applications = Files.walk(sourceDirectory).use { paths ->
      paths
        .filter { Files.isRegularFile(it) }
        .filter { it.fileName.toString().endsWith(".kt") }
        .flatMap { path -> path.discoverApplications().stream() }
        .toList()
    }

    require(applications.isNotEmpty()) {
      "No SPA application definitions found in $sourceDirectory"
    }

    SinglePageApplicationDefinitionValidator.validate(applications)
    return applications
  }

  fun discoverFromSystemProperty(): List<SinglePageApplicationDefinition> {
    val sourceDirectoryPath = System.getProperty(SOURCE_DIRECTORY_PROPERTY)
      ?: throw IllegalArgumentException("'$SOURCE_DIRECTORY_PROPERTY' must be set in task config")
    return discover(Path.of(sourceDirectoryPath))
  }

  private fun Path.discoverApplications(): List<SinglePageApplicationDefinition> {
    val source = Files.readString(this)
    val objectNames = APPLICATION_OBJECT_PATTERN.findAll(source)
      .filter { match -> match.groupValues[2].contains("SinglePageApplicationDefinition") }
      .map { match -> match.groupValues[1] }
      .toList()

    if (objectNames.isEmpty()) {
      return emptyList()
    }

    val packageName = PACKAGE_PATTERN.find(source)?.groupValues?.get(1)
      ?: throw IllegalArgumentException("SPA application definition file must declare a package: $this")

    return objectNames.map { objectName ->
      loadApplicationDefinition("$packageName.$objectName")
    }
  }

  private fun loadApplicationDefinition(className: String): SinglePageApplicationDefinition {
    val instance = Class.forName(className)
      .getField("INSTANCE")
      .get(null)

    require(instance is SinglePageApplicationDefinition) {
      "$className must implement ${SinglePageApplicationDefinition::class.java.name}"
    }

    return instance
  }

  private const val SOURCE_DIRECTORY_PROPERTY = "spa.application.source.dir"
  private val PACKAGE_PATTERN = Regex("""(?m)^\s*package\s+([A-Za-z_][A-Za-z0-9_.]*)\s*$""")
  private val APPLICATION_OBJECT_PATTERN = Regex(
    """object\s+([A-Za-z_][A-Za-z0-9_]*)\s*:\s*([^{]+)\{""",
    setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL)
  )
}
