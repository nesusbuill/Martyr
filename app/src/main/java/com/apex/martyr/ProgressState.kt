package com.apex.martyr

import kotlinx.coroutines.flow.MutableStateFlow

object ProgressState {
    val progress = MutableStateFlow(0)
    val message = MutableStateFlow("")
    val isRunning = MutableStateFlow(false)

    fun start() {
        isRunning.value = true
        update(0, "Connecting to Server...")
    }

    fun update(p: Int, msg: String) {
        progress.value = p
        message.value = msg
    }

    fun finish() {
        isRunning.value = false
        update(100, "Optimization Complete!")
    }

    fun reset() {
        isRunning.value = false
        update(0, "")
    }
}
