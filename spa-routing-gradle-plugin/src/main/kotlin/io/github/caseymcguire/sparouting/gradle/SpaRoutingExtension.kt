package io.github.caseymcguire.sparouting.gradle

import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import javax.inject.Inject

abstract class SpaRoutingExtension @Inject constructor(
  private val ownerProject: Project
) {
  abstract val routeDefinitionsProject: Property<Project>
  abstract val applicationSourceDir: DirectoryProperty
  abstract val clientRoutesOutputDir: DirectoryProperty
  abstract val serverRoutesOutputDir: DirectoryProperty
  abstract val serverRoutesSourceRoot: DirectoryProperty
  abstract val serverRoutesPackage: Property<String>
  abstract val webpackBundleEntriesOutputFile: RegularFileProperty

  fun configuration(action: Action<in SpaRoutingConfiguration>) {
    action.execute(SpaRoutingConfiguration(this, ownerProject))
  }

  fun routeDefinitions(action: Action<in RouteDefinitionsConfiguration>) {
    action.execute(RouteDefinitionsConfiguration(this, ownerProject))
  }

  fun clientRoutes(action: Action<in ClientRoutesConfiguration>) {
    action.execute(ClientRoutesConfiguration(this, ownerProject))
  }

  fun serverRoutes(action: Action<in ServerRoutesConfiguration>) {
    action.execute(ServerRoutesConfiguration(this, ownerProject))
  }

  fun webpackBundleEntries(action: Action<in WebpackBundleEntriesConfiguration>) {
    action.execute(WebpackBundleEntriesConfiguration(this, ownerProject))
  }
}

class SpaRoutingConfiguration internal constructor(
  private val extension: SpaRoutingExtension,
  private val ownerProject: Project
) {
  fun routeDefinitions(action: Action<in RouteDefinitionsConfiguration>) {
    action.execute(RouteDefinitionsConfiguration(extension, ownerProject))
  }

  fun clientRoutes(action: Action<in ClientRoutesConfiguration>) {
    action.execute(ClientRoutesConfiguration(extension, ownerProject))
  }

  fun serverRoutes(action: Action<in ServerRoutesConfiguration>) {
    action.execute(ServerRoutesConfiguration(extension, ownerProject))
  }

  fun webpackBundleEntries(action: Action<in WebpackBundleEntriesConfiguration>) {
    action.execute(WebpackBundleEntriesConfiguration(extension, ownerProject))
  }
}

class RouteDefinitionsConfiguration internal constructor(
  private val extension: SpaRoutingExtension,
  private val ownerProject: Project
) {
  var projectPath: String? = null
    set(value) {
      field = value
      applyConfiguration()
    }

  var sourceDirectory: String = DEFAULT_APPLICATION_SOURCE_DIRECTORY
    set(value) {
      field = value
      sourceDirectoryWasSet = true
      applyConfiguration()
    }

  private var sourceDirectoryWasSet = false

  private fun applyConfiguration() {
    val routeDefinitionsProject = projectPath?.let { ownerProject.project(it) } ?: return
    if (routeDefinitionsProject == ownerProject) {
      throw GradleException(
        "spaRouting.routeDefinitions.projectPath ('$projectPath') must point to a separate " +
          "module, not the module the spa-routing plugin is applied to ('${ownerProject.path}'). " +
          "The generated server routes are compiled into this module, but generating them needs " +
          "the route definitions compiled first, so keeping both in one module creates a " +
          "compileKotlin -> generateServerSpaRoutes -> classes -> compileKotlin cycle. Move your " +
          "SpaApplicationDefinition objects into a dedicated module (commonly " +
          "':spa-route-definitions') and point projectPath at it."
      )
    }
    extension.routeDefinitionsProject.set(routeDefinitionsProject)

    val applicationSourceDir = routeDefinitionsProject.layout.projectDirectory.dir(sourceDirectory)
    if (sourceDirectoryWasSet) {
      extension.applicationSourceDir.set(applicationSourceDir)
    } else {
      extension.applicationSourceDir.convention(applicationSourceDir)
    }
  }

  private companion object {
    const val DEFAULT_APPLICATION_SOURCE_DIRECTORY =
      "src/main/kotlin/com/caseymcguiredotcom/sparoutecontract/applications"
  }
}

class ClientRoutesConfiguration internal constructor(
  private val extension: SpaRoutingExtension,
  private val ownerProject: Project
) {
  var outputDirectory: String? = null
    set(value) {
      field = value
      value?.let { extension.clientRoutesOutputDir.set(ownerProject.directory(it)) }
    }

  fun target(action: Action<in DirectoryTargetConfiguration>) {
    val target = DirectoryTargetConfiguration { directory ->
      extension.clientRoutesOutputDir.set(ownerProject.directory(directory))
    }
    action.execute(target)
  }
}

class ServerRoutesConfiguration internal constructor(
  private val extension: SpaRoutingExtension,
  private val ownerProject: Project
) {
  var packageName: String? = null
    set(value) {
      field = value
      value?.let { extension.serverRoutesPackage.set(it) }
    }

  var sourceRoot: String? = null
    set(value) {
      field = value
      value?.let { extension.serverRoutesSourceRoot.set(ownerProject.directory(it)) }
    }

  fun target(action: Action<in ServerRoutesTargetConfiguration>) {
    action.execute(ServerRoutesTargetConfiguration(extension, ownerProject))
  }
}

class WebpackBundleEntriesConfiguration internal constructor(
  private val extension: SpaRoutingExtension,
  private val ownerProject: Project
) {
  var outputFile: String? = null
    set(value) {
      field = value
      value?.let { extension.webpackBundleEntriesOutputFile.set(ownerProject.file(it)) }
    }

  fun target(action: Action<in FileTargetConfiguration>) {
    val target = FileTargetConfiguration { file ->
      extension.webpackBundleEntriesOutputFile.set(ownerProject.file(file))
    }
    action.execute(target)
  }
}

class DirectoryTargetConfiguration internal constructor(
  private val applyDirectory: (String) -> Unit
) {
  var outputDirectory: String? = null
    set(value) {
      field = value
      value?.let(applyDirectory)
    }

  var directory: String? = null
    set(value) {
      field = value
      value?.let(applyDirectory)
    }
}

class FileTargetConfiguration internal constructor(
  private val applyFile: (String) -> Unit
) {
  var outputFile: String? = null
    set(value) {
      field = value
      value?.let(applyFile)
    }

  var file: String? = null
    set(value) {
      field = value
      value?.let(applyFile)
    }
}

class ServerRoutesTargetConfiguration internal constructor(
  private val extension: SpaRoutingExtension,
  private val ownerProject: Project
) {
  var packageName: String? = null
    set(value) {
      field = value
      value?.let { extension.serverRoutesPackage.set(it) }
    }

  var directory: String? = null
    set(value) {
      field = value
      value?.let {
        extension.serverRoutesSourceRoot.set(ownerProject.directory(it))
      }
    }

  var sourceRoot: String? = null
    set(value) {
      field = value
      value?.let {
        extension.serverRoutesSourceRoot.set(ownerProject.directory(it))
      }
    }
}

private fun Project.directory(path: String): Provider<Directory> =
  providers.provider {
    if (path == "build") {
      layout.buildDirectory.get()
    } else if (path.startsWith("build/")) {
      layout.buildDirectory.get().dir(path.removePrefix("build/"))
    } else {
      layout.projectDirectory.dir(path)
    }
  }

private fun Project.file(path: String): Provider<RegularFile> =
  providers.provider {
    if (path.startsWith("build/")) {
      layout.buildDirectory.get().file(path.removePrefix("build/"))
    } else {
      layout.projectDirectory.file(path)
    }
  }
