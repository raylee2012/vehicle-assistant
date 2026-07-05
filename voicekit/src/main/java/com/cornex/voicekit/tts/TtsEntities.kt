package com.cornex.voicekit.tts

/**
 * TTS合成结果数据定义类
 * @author deng
 * @version 1.0.0
 * @since 2026/5/26
 */
data class SynthesisResult(val seq: Int, val audioData: ByteArray?, val audioLen: Int,
    val status: Int, val engineType: Int, val schedule: String?, val sid: String?) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SynthesisResult

        if (seq != other.seq) return false
        if (audioLen != other.audioLen) return false
        if (status != other.status) return false
        if (engineType != other.engineType) return false
        if (!audioData.contentEquals(other.audioData)) return false
        if (schedule != other.schedule) return false
        if (sid != other.sid) return false

        return true
    }

    override fun hashCode(): Int {
        var result = seq
        result = 31 * result + audioLen
        result = 31 * result + status
        result = 31 * result + engineType
        result = 31 * result + (audioData?.contentHashCode() ?: 0)
        result = 31 * result + (schedule?.hashCode() ?: 0)
        result = 31 * result + (sid?.hashCode() ?: 0)
        return result
    }
}

data class SynthesisError(val errMsg: String?, val code: Int, val sid: String?, val engineType: Int)