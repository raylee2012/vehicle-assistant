package com.cornex.voicekit

import android.app.Activity
import android.content.Context
import android.util.Log
import com.cornex.voicekit.asr.IAsr
import com.cornex.voicekit.api.IInitResult
import com.cornex.voicekit.api.IPermissionListener
import com.cornex.voicekit.tts.ITts
import com.cornex.voicekit.api.IVoiceKit
import com.cornex.voicekit.asr.AsrImpl
import com.cornex.voicekit.constants.VoiceKitDef
import com.cornex.voicekit.tts.TtsImpl
import com.hjq.permissions.OnPermission
import com.hjq.permissions.XXPermissions
import com.iflytek.sparkchain.core.LogLvl
import com.iflytek.sparkchain.core.SparkChain
import com.iflytek.sparkchain.core.SparkChainConfig

/**
 * 语音能力实现类
 * @author deng
 * @version 1.0.0
 * @since 2026/6/1
 */
class VoiceKitImpl : IVoiceKit {
    private val mSparkChainConfig: SparkChainConfig = SparkChainConfig.builder()
    private var mContext: Context? = null
    private val mAsr: IAsr = AsrImpl()
    private val mTts: ITts = TtsImpl()

    override fun init(appContext: Context, listener: IInitResult) {
        this.mContext = appContext
        mSparkChainConfig.appID(VoiceKitDef.APP_ID)
            .apiKey(VoiceKitDef.APP_KEY)
            .apiSecret(VoiceKitDef.APP_SECRET)
            .logLevel(LogLvl.VERBOSE.value)
            .logPath(VoiceKitDef.SPARK_LOG_PATH)
        val ret = SparkChain.getInst().init(appContext, mSparkChainConfig)
        if (ret == 0) {
            mAsr.initAsr(appContext)
            mTts.initTts()
            listener.onSuccess()
        } else {
            listener.onFail(VoiceKitDef.INIT_FAIL_MSG + ret)
        }
    }

    override fun permissionRequest(
        activityContext: Context,
        permissionList: MutableList<String?>?,
        listener: IPermissionListener) {
        if (activityContext !is Activity) {
            Log.e(TAG, "please ensure activityContext is Activity")
            return
        }
        XXPermissions.with(activityContext).permission(permissionList)
            .request(object : OnPermission {
                override fun hasPermission(granted: MutableList<String?>, all: Boolean) {
                    if (HashSet(granted).containsAll(VoiceKitDef.LOG_PERMISSION_LIST)) {
                        Log.i(TAG, "write and read log permission granted")
                    }
                    listener.hasPermission(granted, all)
                }

                override fun noPermission(denied: MutableList<String?>, quick: Boolean) {
                    if (quick) {
                        Log.e(
                            TAG,
                            "your request has been permanently rejected. please manually grant the permission"
                        )
                        XXPermissions.startPermissionActivity(activityContext, denied)
                    }
                    listener.noPermission(denied, quick)
                }
            })
    }

    override fun asr(): IAsr {
        return mAsr
    }

    override fun tts(): ITts {
        return mTts
    }

    override fun dispose() {
        SparkChain.getInst().unInit()
        mAsr.dispose()
        mTts.dispose()
    }

    companion object {
        private val TAG: String = VoiceKitImpl::class.java.getSimpleName()
    }
}
