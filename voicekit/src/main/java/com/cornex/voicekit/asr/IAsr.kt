package com.cornex.voicekit.asr

import android.content.Context

/**
 * ASR能力接口
 * @author deng
 * @version 1.0.0
 * @since 2026/5/22
 */
interface IAsr {
    /**
     * 初始化ASR能力
     */
    fun initAsr(context: Context)

    /**
     * 设置ASR结果监听器
     * @param listener ASR结果监听器
     */
    fun setOnAsrResultListener(listener: IAsrResultListener?)

    /**
     * 开始录音
     */
    fun startRecord()

    /**
     * 停止录音
     */
    fun stopRecord()

    /**
     * 是否正在录音
     * @return 是否正在录音 true 录音中
     */
    fun isRecording() : Boolean

    /**
     * 释放资源
     */
    fun dispose()
}
