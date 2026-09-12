import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  id("org.jetbrains.kotlin.jvm")
  `java-library`
  id("com.vanniktech.maven.publish")
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(21))
  }
}

kotlin {
  jvmToolchain(21)
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_21)
  }
}

dependencies {
  testImplementation(kotlin("test"))
  testImplementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.2.21")
}

val typeScriptTestDirectory = layout.buildDirectory.dir("typescript-tests")
val prepareTypeScriptTests by tasks.registering(Copy::class) {
  from("src/test/typescript/package.json", "src/test/typescript/package-lock.json")
  into(typeScriptTestDirectory)
}

val installTypeScriptTestDependencies by tasks.registering(Exec::class) {
  dependsOn(prepareTypeScriptTests)
  workingDir(typeScriptTestDirectory)
  commandLine("npm", "ci", "--ignore-scripts", "--no-audit", "--no-fund")
  inputs.files("src/test/typescript/package.json", "src/test/typescript/package-lock.json")
  outputs.dir(typeScriptTestDirectory.map { it.dir("node_modules") })
}

tasks.test {
  dependsOn(installTypeScriptTestDependencies)
  useJUnitPlatform()
  inputs.dir("src/test/fixtures")
  inputs.dir("src/test/typescript")
  systemProperty("test.runtime.classpath", sourceSets["test"].runtimeClasspath.asPath)
  systemProperty("test.typescript.compiler", typeScriptTestDirectory.get().file("node_modules/typescript/bin/tsc").asFile.absolutePath)
}

mavenPublishing {
  publishToMavenCentral()

  if (hasSigningCredentials()) {
    signAllPublications()
  }

  coordinates(
    groupId = "io.github.caseymcguire",
    artifactId = "spa-routing-core",
    version = project.version.toString()
  )

  pom {
    name.set("spa-routing-core")
    description.set("Reusable SPA routing contract and code generators.")
    url.set("https://github.com/caseymcguire/spa-routing")

    licenses {
      license {
        name.set("The Apache License, Version 2.0")
        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
      }
    }

    developers {
      developer {
        id.set("caseymcguire")
        name.set("Casey McGuire")
        url.set("https://github.com/caseymcguire")
      }
    }

    scm {
      url.set("https://github.com/caseymcguire/spa-routing")
      connection.set("scm:git:git://github.com/caseymcguire/spa-routing.git")
      developerConnection.set("scm:git:ssh://git@github.com/caseymcguire/spa-routing.git")
    }
  }
}

fun hasSigningCredentials(): Boolean {
  return providers.gradleProperty("signingInMemoryKey").isPresent ||
    providers.gradleProperty("signing.secretKeyRingFile").isPresent
}
