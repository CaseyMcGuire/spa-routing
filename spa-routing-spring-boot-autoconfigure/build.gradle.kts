import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  id("org.jetbrains.kotlin.jvm")
  `java-library`
  id("com.vanniktech.maven.publish")
}

val springBootVersion: String by rootProject.extra
val springFrameworkVersion: String by rootProject.extra

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
  api(project(":spa-routing-core"))

  compileOnlyApi("org.springframework.boot:spring-boot:$springBootVersion")
  compileOnlyApi("org.springframework.boot:spring-boot-autoconfigure:$springBootVersion")
  compileOnlyApi("org.springframework:spring-webmvc:$springFrameworkVersion")

  annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:$springBootVersion")

  testImplementation(kotlin("test"))
  testImplementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("org.springframework.boot:spring-boot-starter-web")
}

tasks.test {
  useJUnitPlatform()
}

mavenPublishing {
  publishToMavenCentral()

  if (hasSigningCredentials()) {
    signAllPublications()
  }

  coordinates(
    groupId = "io.github.caseymcguire",
    artifactId = "spa-routing-spring-boot-autoconfigure",
    version = project.version.toString()
  )

  pom {
    name.set("spa-routing-spring-boot-autoconfigure")
    description.set("Spring Boot auto-configuration for SPA routing.")
    url.set("https://github.com/CaseyMcGuire/spa-routing")

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
        url.set("https://github.com/CaseyMcGuire")
      }
    }

    scm {
      url.set("https://github.com/CaseyMcGuire/spa-routing")
      connection.set("scm:git:git://github.com/CaseyMcGuire/spa-routing.git")
      developerConnection.set("scm:git:ssh://git@github.com:CaseyMcGuire/spa-routing.git")
    }
  }
}

fun hasSigningCredentials(): Boolean {
  return providers.gradleProperty("signingInMemoryKey").isPresent ||
    providers.gradleProperty("signing.secretKeyRingFile").isPresent
}
