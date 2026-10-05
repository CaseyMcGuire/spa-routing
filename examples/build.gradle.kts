plugins {
  base
}

tasks.named("build") {
  dependsOn(
    ":examples:route-definitions:build",
    ":examples:blog:build",
    ":examples:frontend:build",
    ":examples:spring:build",
    ":examples:ktor:build"
  )
}
