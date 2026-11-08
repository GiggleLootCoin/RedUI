package com.giggleloot.redui

data class PerformanceSettings(
    val temperature: Float = .70f,
    val maxTokens: Int = 256,
    val topP: Float = .90f,
    val topK: Int = 40,
    val repeatPenalty: Float = 1.10f,
    val contextLength: Int = 1024,
    val numThreads: Int = 4,
    val useMmap: Boolean = true,
    val flashAttention: Boolean = false,
    val batchSize: Int = 32,
    val gpuLayers: Int = 0,
)
