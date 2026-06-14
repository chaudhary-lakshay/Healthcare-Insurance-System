package com.lakshay.healthcare.shared.lifecycle

// Case lifecycle states. Only the ones reachable today live here; later phases add more.
enum class CaseStatus {
    SUBMITTED,
    DETERMINED
}
