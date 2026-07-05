package com.cornex.voicekit

import android.content.Context
import com.cornex.voicekit.api.IInitResult
import com.cornex.voicekit.api.IPermissionListener
import com.cornex.voicekit.api.IVoiceKit
import com.cornex.voicekit.asr.IAsr
import com.cornex.voicekit.tts.ITts
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 语音能力管理器
 * @author deng
 * @version 1.1.0
 * @since 2026/6/1
 */
@Singleton
class VoiceKitManager @Inject constructor() {
    private val mVoiceKit: IVoiceKit = VoiceKitImpl()

    fun init(appContext: Context, listener: IInitResult) {
        mVoiceKit.init(appContext, listener)
    }

    fun permissionRequest(
        activityContext: Context,
        permissionList: MutableList<String?>?,
        listener: IPermissionListener) {
        mVoiceKit.permissionRequest(activityContext, permissionList, listener)
    }

    fun asr(): IAsr {
        return mVoiceKit.asr()
    }

    fun tts(): ITts {
        return mVoiceKit.tts()
    }

    fun dispose() {
        mVoiceKit.dispose()
    }
}
