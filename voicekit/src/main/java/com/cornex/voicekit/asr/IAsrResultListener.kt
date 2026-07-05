package com.cornex.voicekit.asr

import com.cornex.voicekit.constants.VoiceKitDef.AsrResultStatus

/**
 * ASR结果监听器
 * @author deng
 * @version 1.0.0
 * @since 2026/5/22
 */
interface IAsrResultListener {
    /**
     * 识别或录音失败
     * @param errorMsg 错误信息
     */
    fun onFail(errorMsg: String?)

    /**
     * 开始识别或录音
     */
    fun onStart()

    /**
     * 识别或录音结果
     * @param status 状态
     * @param result 结果
     */
    fun onResult(@AsrResultStatus status: Int, result: String?)

    /**
     * 识别完成
     */
    fun onFinish()
}
