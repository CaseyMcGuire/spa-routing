package com.sparouting.contract

/**
 * Application-defined explanation for a navigation denial.
 * [code] identifies the reason for clients; [message] is suitable for display to the user.
 */
data class DenialReason(
  val code: String,
  val message: String
)
