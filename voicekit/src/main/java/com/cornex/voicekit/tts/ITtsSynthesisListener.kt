package com.cornex.voicekit.tts

/**
 * TTS合成结果监听器
 * @author deng
 * @version 1.0.0
 * @since 2026/5/26
 */
interface ITtsSynthesisListener {
    /**
     * 合成结果
     * @param result 合成结果
     */
    fun onResult(result: SynthesisResult)

    /**
     * 合成错误
     * @param error 合成错误
     */
    fun onError(error: SynthesisError)
}