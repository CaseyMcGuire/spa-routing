plugins {
  base
}

tasks.named("build") {
  dependsOn(":examples:route-definitions:build", ":examples:spring:build")
}
