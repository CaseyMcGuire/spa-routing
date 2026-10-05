import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  id("org.jetbrains.kotlin.jvm")
  application
}

val springBootVersion: String by rootProject.extra

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

application {
  mainClass.set("com.sparouting.examples.spring.SpringExampleApplicationKt")
}

dependencies {
  implementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
  implementation(project(":examples:blog"))
  runtimeOnly(project(":examples:frontend"))
  implementation(project(":spa-routing-spring-boot-starter"))
  implementation("tools.jackson.module:jackson-module-kotlin")
}
