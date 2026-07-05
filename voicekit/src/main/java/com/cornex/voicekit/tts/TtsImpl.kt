package com.cornex.voicekit.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.util.Log
import com.cornex.voicekit.constants.VoiceKitDef
import com.iflytek.sparkchain.core.tts.OnlineTTS
import com.iflytek.sparkchain.core.tts.TTS.TTSError
import com.iflytek.sparkchain.core.tts.TTS.TTSResult
import com.iflytek.sparkchain.core.tts.TTSCallbacks
import java.io.File
import java.util.concurrent.CompletableFuture

/**
 * TTS合成并播报能力实现类
 * @author deng
 * @version 1.0.0
 * @since 2026/5/26
 */
class TtsImpl : ITts {
    private var mAudioTrack: AudioTrack? = null
    @Volatile
    private var mIsPlaying = false
    private var mOnlineTTS: OnlineTTS? = null
    private var mTtsListener: ITtsSynthesisListener? = null
    private var mHandler: Handler? = null

    override fun initTts() {
        val folder = File(VoiceKitDef.TTS_WORK_DIR)
        if (!folder.exists()) {
            val success = folder.mkdirs()
            if (!success) {
                Log.e(TAG, "tts create folder failed")
                return
            }
        }
        Log.d(TAG, "tts folder exists and path is : " + VoiceKitDef.TTS_WORK_DIR)
        val minBufferSize = AudioTrack.getMinBufferSize(
            VoiceKitDef.SAMPLE_RATE,
            VoiceKitDef.CHANNEL_CONFIG,
            VoiceKitDef.AUDIO_FORMAT
        )
        mAudioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(VoiceKitDef.AUDIO_FORMAT)
                    .setSampleRate(VoiceKitDef.SAMPLE_RATE)
                    .setChannelMask(VoiceKitDef.CHANNEL_CONFIG)
                    .build()
            )
            .setBufferSizeInBytes(minBufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        val dbThread = HandlerThread(TAG)
        dbThread.start()
        mHandler = object : Handler(dbThread.getLooper()) {
            override fun handleMessage(msg: Message) {
                super.handleMessage(msg)
                when (msg.what) {
                    AUDIO_PLAY_START -> {
                        Log.d(TAG, "audio play start")
                        mAudioTrack?.play()
                    }
                    AUDIO_PLAY_WRITE -> {
                        val bundle = msg.obj as Bundle
                        val audioData = bundle.getByteArray(VoiceKitDef.AUDIO_DATA_KEY)
                        if (audioData != null && audioData.isNotEmpty()) {
                            mAudioTrack?.write(audioData, 0, audioData.size)
                        }
                    }
                    AUDIO_PLAY_END -> {
                        Log.d(TAG, "audio play end")
                        mAudioTrack?.stop()
                        mIsPlaying = false
                    }
                }
            }
        }
    }

    private val mTTSCallback: TTSCallbacks = object : TTSCallbacks {
        override fun onResult(result: TTSResult, `object`: Any?) {
            val audio = result.data
            val audioLen = result.len
            val status = result.status
            Log.d(TAG, "status：" + result.status)
            val bundle = Bundle()
            bundle.putByteArray(VoiceKitDef.AUDIO_DATA_KEY, audio)
            if (mHandler == null) {
                return
            }
            val msg = mHandler!!.obtainMessage()
            msg.what = AUDIO_PLAY_WRITE
            msg.obj = bundle
            mHandler!!.sendMessage(msg)
            if (status == VoiceKitDef.TTS_SYNTHESIS_STATUS_END) {
                mHandler!!.sendEmptyMessage(AUDIO_PLAY_END)
            }
            mTtsListener?.onResult(SynthesisResult(
                result.seq, audio, audioLen,
                status, result.engineType, result.ced, result.sid
            ))
        }

        override fun onError(ttsError: TTSError, `object`: Any?) {
            val errCode = ttsError.code
            val errMsg = ttsError.errMsg
            val sid = ttsError.sid
            Log.e(TAG, "onError msg is $errMsg")
            if (mIsPlaying) {
                stopPlay()
            }
            mTtsListener?.onError(SynthesisError(errMsg, errCode, sid, ttsError.engineType))
        }
    }

    override fun startPlay(ttsText: String) {
        if (mAudioTrack == null) {
            Log.w(TAG, "tts init failed, please check")
            return
        }
        if (mIsPlaying) {
            Log.w(TAG, "tts is playing, please stop first")
            stopPlay()
        }
        mIsPlaying = true
        mHandler!!.sendEmptyMessage(AUDIO_PLAY_START)
        if (mOnlineTTS == null) {
            mOnlineTTS = OnlineTTS(VoiceKitDef.VCN)
            mOnlineTTS!!.speed(VoiceKitDef.SPEED)
            mOnlineTTS!!.pitch(VoiceKitDef.PITCH)
            mOnlineTTS!!.volume(VoiceKitDef.VOLUME)
            // 合成音频的背景音 0:无背景音（默认值） 1:有背景音
            mOnlineTTS!!.bgs(0)
            mOnlineTTS!!.registerCallbacks(mTTSCallback)
        }
        CompletableFuture.supplyAsync {
            mOnlineTTS!!.aRun(ttsText)
        }.thenAccept { ret ->
            if (ret != 0) {
                mIsPlaying = false
                Log.e(TAG, "tts aRun failed, ret is : $ret")
                mTtsListener?.onError(SynthesisError("tts aRun failed", ret, "", 0))
                return@thenAccept
            }
            Log.d(TAG, "tts aRun success")
        }
    }

    override fun feedTextPlay(ttsText: String) {

    }

    override fun stopPlay() {
        if (!mIsPlaying) {
            Log.w(TAG, "tts is not playing, please start first")
            return
        }
        mIsPlaying = false
        if (mOnlineTTS != null) {
            mHandler?.removeCallbacksAndMessages(null)
            mHandler?.sendEmptyMessage(AUDIO_PLAY_END)
            mOnlineTTS = null
        }
    }

    override fun setOnTtsResultListener(listener: ITtsSynthesisListener?) {
        this.mTtsListener = listener
    }

    override fun dispose() {
        if (mHandler != null) {
            stopPlay()
            mHandler!!.looper.quitSafely()
            mHandler = null
            mAudioTrack!!.release()
            mAudioTrack = null
        }
    }

    companion object {
        private val TAG: String = TtsImpl::class.java.getSimpleName()
        private const val AUDIO_PLAY_START = 1
        private const val AUDIO_PLAY_WRITE = 2
        private const val AUDIO_PLAY_END = 3
    }
}
