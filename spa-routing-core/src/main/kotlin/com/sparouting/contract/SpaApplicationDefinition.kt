package com.sparouting.contract

interface SpaApplicationDefinition {
  /** Unique identifier for this single page application.  */
  val id: String

  /** The human readable name of this application e.g. Account */
  val name: String

  /**
   * URL path prefix without leading or trailing slashes, such as `account`. Routes are defined relative to this, e.g.
   *
   *  - /account
   *  - /account/setting
   *  - /account/edit
   *
   * Use an empty string to serve routes at the site root
   */
  val urlPrefix: String

  /** File path to the root frontend React component */
  val appRootPath: String

  /** Routes with paths relative to [urlPrefix] and IDs unique within this application. */
  val routes: List<SpaRouteDefinition>

  /**
   * Bundler entry name, also used by the default HTML renderer for asset names
   * such as `account.bundle.js` and `account.css`. Defaults to [id].
   */
  val bundleName: String
    get() = id

  /** Returns the prefixed path pattern, preserving parameter placeholders. */
  fun getFullPathPattern(route: SpaRouteDefinition): String {
    return when {
      route.path.isEmpty() -> "/$urlPrefix"
      urlPrefix.isEmpty() -> "/${route.path}"
      else -> "/$urlPrefix/${route.path}"
    }
  }
}
