package com.giggleloot.redui

data class PerformanceSettings(
    val temperature: Float = .62f,
    val maxTokens: Int = 160,
    val topP: Float = .86f,
    val topK: Int = 28,
    val repeatPenalty: Float = 1.08f,
    val contextLength: Int = 1024,
    val numThreads: Int = 4,
    val useMmap: Boolean = true,
    val flashAttention: Boolean = false,
    val batchSize: Int = 64,
    val gpuLayers: Int = 0,
    val minP: Float = .05f
)
