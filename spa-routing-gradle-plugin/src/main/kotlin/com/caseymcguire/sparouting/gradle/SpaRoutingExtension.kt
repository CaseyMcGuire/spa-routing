package com.caseymcguire.sparouting.gradle

import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property

abstract class SpaRoutingExtension {
  abstract val routeDefinitionsProject: Property<Project>
  abstract val applicationSourceDir: DirectoryProperty
  abstract val clientRoutesOutputDir: DirectoryProperty
  abstract val serverRoutesOutputDir: DirectoryProperty
  abstract val serverRoutesSourceRoot: DirectoryProperty
  abstract val serverRoutesPackage: Property<String>
  abstract val webpackBundleEntriesOutputFile: RegularFileProperty
}
