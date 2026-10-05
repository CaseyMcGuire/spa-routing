package com.sparouting.runtime.config

import com.sparouting.contract.SinglePageApplicationManifest

/** Runtime configuration around a generated manifest. Access handlers are registered separately. */
interface SinglePageApplicationConfig {
  val manifest: SinglePageApplicationManifest
}
