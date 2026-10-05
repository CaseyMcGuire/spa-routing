import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  id("org.jetbrains.kotlin.jvm")
  application
}

val ktorVersion: String by rootProject.extra

kotlin {
  jvmToolchain(21)
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_21)
  }
}

application {
  mainClass.set("com.sparouting.examples.ktor.KtorExampleApplicationKt")
}

dependencies {
  implementation(project(":examples:blog"))
  runtimeOnly(project(":examples:frontend"))
  implementation(project(":spa-routing-ktor"))
  implementation(platform("io.ktor:ktor-bom:$ktorVersion"))
  implementation("io.ktor:ktor-server-netty")
  implementation("io.ktor:ktor-server-content-negotiation")
  implementation("io.ktor:ktor-server-status-pages")
  implementation("io.ktor:ktor-serialization-jackson")
  runtimeOnly("org.slf4j:slf4j-simple:2.0.19")
}
