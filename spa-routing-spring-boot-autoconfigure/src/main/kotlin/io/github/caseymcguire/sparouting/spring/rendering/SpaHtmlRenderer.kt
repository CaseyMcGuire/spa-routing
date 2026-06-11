package io.github.caseymcguire.sparouting.spring.rendering

import io.github.caseymcguire.sparouting.spring.config.SinglePageApplicationConfig
import org.springframework.web.servlet.function.ServerResponse

interface SpaHtmlRenderer {
  fun render(application: SinglePageApplicationConfig): ServerResponse
}
