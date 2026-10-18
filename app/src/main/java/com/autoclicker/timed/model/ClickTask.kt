package com.autoclicker.timed.model

import java.io.Serializable

data class ClickTask(
    val targetTimeMillis: Long,
    val x: Float,
    val y: Float,
    val repeatCount: Int = 1,
    val repeatIntervalMs: Long = 100L,
    val durationMs: Long = 50L
) : Serializable
