package com.cornex.voicekit.asr

import android.content.Context
import android.util.Log
import com.cornex.voicekit.constants.VoiceKitDef
import com.cornex.voicekit.util.AudioRecorderManager
import com.hjq.permissions.XXPermissions
import com.iflytek.sparkchain.core.asr.ASR
import com.iflytek.sparkchain.core.asr.AsrCallbacks
import com.iflytek.sparkchain.core.asr.AudioAttributes
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ASR能力实现类
 * @author deng
 * @version 1.0.0
 * @since 2026/5/22
 */
class AsrImpl : IAsr, AudioRecorderManager.AudioDataCallback {
    private val mIsRecordWrite = AtomicBoolean(false)
    private var mContext: Context? = null
    private var mAsrResultListener: IAsrResultListener? = null
    private var mAudioRecorderManager: AudioRecorderManager? = null
    private var mAsr: ASR? = null
    private val mAttr: AudioAttributes = AudioAttributes()
    private var mRecordCount = 0

    override fun initAsr(context: Context) {
        this.mContext = context
        if (mAsr == null) {
            mAsr = ASR()
            mAsr!!.language(VoiceKitDef.LANGUAGE_ZH_CN)
            // 应用领域,iat:日常用语
            mAsr!!.domain(VoiceKitDef.APPLICATION_FIELD)
            // 方言，mandarin:普通话
            mAsr!!.accent(VoiceKitDef.DIALECT_MANDARIN)
            // 返回子句结果对应的起始和结束的端点帧偏移值。
            mAsr!!.vinfo(true)
            // 开启结果动态纠正
            mAsr!!.dwa(VoiceKitDef.DYNAMIC_CORRECTION)
            mAsr!!.registerCallbacks(mAsrCallbacks)
        }
    }

    override fun setOnAsrResultListener(listener: IAsrResultListener?) {
        this.mAsrResultListener = listener
    }

    private val mAsrCallbacks: AsrCallbacks = object : AsrCallbacks {
        override fun onResult(asrResult: ASR.ASRResult, `object`: Any?) {
            val status = asrResult.status
            Log.i(TAG, "asr result status is " + asrResult.status)
            val result = asrResult.bestMatchText
            mAsrResultListener?.onResult(status, result)
            if (status == VoiceKitDef.FINAL_PART) {
                Log.i(TAG, "asr result is final part")
                mAsrResultListener?.onFinish()
                stopRecord()
            }
        }

        override fun onError(asrError: ASR.ASRError, `object`: Any?) {
            val code = asrError.code
            val msg = asrError.errMsg
            mAsrResultListener?.onFail("error code is $code and error msg is $msg")
            stopRecord()
        }

        override fun onBeginOfSpeech() {
        }

        override fun onEndOfSpeech() {
        }
    }

    override fun startRecord() {
        if (mContext == null || !XXPermissions.hasPermission(mContext, VoiceKitDef.RECORD_PERMISSION)) {
            Log.w(TAG, "please ensure app has record permission or init success")
            return
        }
        if (mAsr == null) {
            Log.w(TAG, "asr is null, please init first")
            return
        }
        CompletableFuture.supplyAsync {
            Log.d(TAG, "start asr engine")
            mAsr!!.start(mAttr, (++mRecordCount).toString())
        }.thenAccept { ret ->
            Log.d(TAG, "start asr engine build complete")
            if (ret != RECORD_SUCCESS) {
                Log.w(TAG, "record start fail: error code is $ret")
                mAsrResultListener?.onFail("record start fail: error code is $ret")
                return@thenAccept
            }
            mIsRecordWrite.set(true)
            if (mAudioRecorderManager == null) {
                mAudioRecorderManager = AudioRecorderManager.getInstance()
            }
            mAudioRecorderManager!!.startRecord()
            mAudioRecorderManager!!.registerCallBack(this)
            mAsrResultListener?.onStart()
        }
    }

    override fun stopRecord() {
        mIsRecordWrite.set(false)
        if (mAsr == null) {
            Log.w(TAG, "asr is null, please start record first")
            return
        }
        mAudioRecorderManager?.let {
            mAudioRecorderManager!!.stopRecord()
            mAudioRecorderManager = null
        }
        mAsr!!.stop(true)
        Log.i(TAG, "asr stop success")
    }

    override fun isRecording(): Boolean {
        return mIsRecordWrite.get()
    }

    override fun onAudioData(data: ByteArray?, size: Int) {
        if (mIsRecordWrite.get()) {
            val ret = mAsr!!.write(data)
            if (ret != 0) {
                mIsRecordWrite.set(false)
            }
        }
    }

    override fun onAudioVolume(db: Double, volume: Int) {
    }

    override fun dispose() {
        mAsr = null
    }

    companion object {
        private val TAG: String = AsrImpl::class.java.getSimpleName()
        private const val RECORD_SUCCESS = 0
    }
}