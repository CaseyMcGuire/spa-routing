package com.sparouting.spring.rendering

import com.sparouting.contract.SinglePageApplicationConfig
import org.springframework.web.servlet.function.ServerResponse

interface HtmlRenderer {
  fun render(application: SinglePageApplicationConfig): ServerResponse
}
