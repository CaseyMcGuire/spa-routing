package com.sparouting.spring.rendering

import com.sparouting.runtime.config.SinglePageApplicationConfig
import org.springframework.web.servlet.function.ServerResponse

interface HtmlRenderer {
  fun render(application: SinglePageApplicationConfig): ServerResponse
}
