package com.sparouting.contract

/** Runtime application metadata implemented by generated code, independent of source definitions. */
interface SinglePageApplicationManifest {
  val id: String
  val name: String
  val bundleName: String
  val routes: List<RouteManifest>
}
