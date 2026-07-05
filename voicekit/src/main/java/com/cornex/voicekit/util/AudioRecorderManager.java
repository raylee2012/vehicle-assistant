package com.cornex.voicekit.util;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 音频录制管理类
 * @author deng
 * @version 1.0.0
 * @since 2026/5/22
 */
public class AudioRecorderManager {
    private final String TAG = "AudioRecorder";
    private static volatile AudioRecorderManager mInstance;
    private final int mBufferSize;
    private final AudioRecord mRecorder;
    private final AtomicBoolean mIsStart = new AtomicBoolean();
    private AudioDataCallback mCallback;

    private AudioRecorderManager() {
        this(16000, AudioFormat.ENCODING_PCM_16BIT, AudioFormat.CHANNEL_IN_MONO);
    }

    @SuppressLint("MissingPermission")
    private AudioRecorderManager(int sampleRateInHz, int audioFormat, int channels) {
        mBufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channels, audioFormat);
        mRecorder = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRateInHz, channels, audioFormat, mBufferSize);
    }

    public static AudioRecorderManager getInstance() {
        if (mInstance == null) {
            synchronized (AudioRecorderManager.class) {
                if (mInstance == null) {
                    mInstance = new AudioRecorderManager();
                }
            }
        }
        return mInstance;
    }

    public void registerCallBack(AudioDataCallback callback) {
        this.mCallback = callback;
    }

    public void startRecord() {
        CompletableFuture.runAsync(() -> {
            if (mRecorder.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.w(TAG,"record state exception: " + mRecorder.getState());
                stopRecord();
                return;
            }
            try {
                int bytesRecord;
                byte[] tempBuffer = new byte[mBufferSize];
                mIsStart.set(true);
                mRecorder.startRecording();
                while (mIsStart.get()) {
                    bytesRecord = mRecorder.read(tempBuffer, 0, mBufferSize);
                    if (bytesRecord == AudioRecord.ERROR_INVALID_OPERATION || bytesRecord == AudioRecord.ERROR_BAD_VALUE) {
                        continue;
                    }
                    if (bytesRecord == AudioRecord.ERROR || bytesRecord == 0 || !mIsStart.get()) {
                        Log.i(TAG, "record error: bytesRecord is " + bytesRecord);
                        break;
                    }
                    // 在此可以对录制音频的数据进行二次处理 比如变声，压缩，降噪，增益等操作
                    double sumSquares = 0.0;
                    // 每个样本16位(2字节)
                    int sampleCount = bytesRecord / 2;
                    for (int i = 0; i < bytesRecord; i += 2) {
                        // 将两个字节转换为一个16位短整型
                        short sample = (short) ((tempBuffer[i] & 0xFF) | ((tempBuffer[i + 1] & 0xFF) << 8));
                        // 计算平方和
                        sumSquares += (double)sample * sample;
                    }

                    // 计算RMS (均方根)
                    double rms = Math.sqrt(sumSquares / sampleCount);
                    // 转换为分贝值 (防止除以0)
                    double db = -120.0;
                    if (rms > 1e-10) {
                        // 避免log(0)
                        db = 20 * Math.log10(rms / 32767.0);
                    }
                    // 映射到0-9音量等级
                    int volume = 0;
                    if (db > -60) {
                        // 更符合人耳感知的映射：-60dB(0级)到-20dB(9级)
                        volume = (int) Math.min(9, Math.max(0, (db + 60) * 9 / 40.0));
                    }
                    mCallback.onAudioVolume(db,volume);
                    // 直接将pcm音频原数据写入文件 这里可以直接发送至服务器 对方采用AudioTrack进行播放原数据
                    mCallback.onAudioData(tempBuffer,bytesRecord);
                }
            } catch (Exception e) {
                Log.e(TAG,"record error : " + e.getMessage());
            } finally {
                mRecorder.stop();
                mRecorder.release();
            }
        });
    }

    public void stopRecord() {
        mIsStart.set(false);
        if (mRecorder != null) {
            if (mRecorder.getState() == AudioRecord.STATE_INITIALIZED) {
                mRecorder.stop();
            }
            mRecorder.release();
        }
        mInstance = null;
    }

    public interface AudioDataCallback {
        void onAudioData(byte[] data, int size);

        void onAudioVolume(double db,int volume);
    }
}