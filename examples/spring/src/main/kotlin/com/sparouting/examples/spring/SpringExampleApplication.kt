package com.sparouting.examples.spring

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(proxyBeanMethods = false)
class SpringExampleApplication

fun main(args: Array<String>) {
  runApplication<SpringExampleApplication>(*args)
}
