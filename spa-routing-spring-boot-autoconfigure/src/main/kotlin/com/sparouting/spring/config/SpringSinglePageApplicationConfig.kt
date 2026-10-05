package com.sparouting.spring.config

import com.sparouting.runtime.config.SinglePageApplicationConfig
import org.springframework.web.servlet.function.ServerResponse

/** Optional Spring-specific rendering hook; ordinary configurations can use the shared interface. */
interface SpringSinglePageApplicationConfig : SinglePageApplicationConfig {
  /** Return null to use the configured HtmlRenderer bean after access is allowed. */
  fun renderHtml(): ServerResponse? = null
}
