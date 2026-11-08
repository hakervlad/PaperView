package com.example.aiknowledgefeed.backend.services

/** Presentation of local storage and the explicitly requested external model test. */
data class ConnectionStep(val title: String, val status: String = "Ausstehend", val detail: String = "")
