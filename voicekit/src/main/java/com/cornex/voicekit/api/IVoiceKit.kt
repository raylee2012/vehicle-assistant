package com.cornex.voicekit.api

import android.content.Context
import com.cornex.voicekit.asr.IAsr
import com.cornex.voicekit.tts.ITts

interface IVoiceKit {
    /**
     * 初始化VoiceKit能力
     * @param appContext 应用上下文
     * @param listener 初始化结果监听器
     */
    fun init(appContext: Context, listener: IInitResult)

    /**
     * 申请权限
     * @param activityContext 活动上下文
     * @param permissionList 权限列表
     * @param listener 权限申请结果监听器
     */
    fun permissionRequest(
        activityContext: Context, permissionList: MutableList<String?>?,
        listener: IPermissionListener
    )

    /**
     * 获取ASR能力
     * @return ASR能力接口
     */
    fun asr() : IAsr

    /**
     * 获取TTS能力
     * @return TTS能力接口
     */
    fun tts() : ITts

    /**
     * 释放资源
     */
    fun dispose()
}
