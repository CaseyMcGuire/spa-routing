plugins {
  base
}

tasks.named("build") {
  dependsOn(
    ":examples:route-definitions:build",
    ":examples:frontend:build",
    ":examples:spring:build"
  )
}
