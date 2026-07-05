package com.cornex.voicekit.constants;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.Manifest;
import android.media.AudioFormat;
import android.os.Environment;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.util.Arrays;
import java.util.List;

/**
 * 语音能力常量类
 * @author deng
 * @version 1.0.0
 * @since 2026/5/22
 */
public class VoiceKitDef {
    public static final String INIT_FAIL_MSG = "SDK初始化失败,错误码:";
    public static final String APP_ID = "a16572bf";
    public static final String APP_KEY = "69c92b8806b4da4cd9f6242aa8137c11";
    public static final String APP_SECRET = "NTYxZjA3MDVlZGJjNWIwNDkwYmVkM2Yw";
    public static List<String> LOG_PERMISSION_LIST = Arrays.asList(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.MANAGE_EXTERNAL_STORAGE);
    public static final String RECORD_PERMISSION = Manifest.permission.RECORD_AUDIO;
    public static String SPARK_LOG_PATH = Environment.getExternalStorageDirectory().getAbsolutePath() + "/iflytek/SparkChain.log";
    // ASR
    public static final String LANGUAGE_ZH_CN = "zh_cn";
    public static final String APPLICATION_FIELD = "iat";
    public static final String DIALECT_MANDARIN = "mandarin";
    public static final String DYNAMIC_CORRECTION = "wpgs";

    public static final int FIRST_PART = 0;
    public static final int MIDDLE_PART = 1;
    public static final int FINAL_PART = 2;
    @Retention(SOURCE)
    @IntDef({FIRST_PART, MIDDLE_PART, FINAL_PART})
    public @interface AsrResultStatus {}

    // TTS
    public static final String TTS_WORK_DIR = Environment.getExternalStorageDirectory().getAbsolutePath() + "/iflytek/Onlinetts";
    public static final int SAMPLE_RATE = 16000;
    // 单声道输出
    public static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO;
    // PCM 16位编码
    public static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    public static final String VCN = "xiaoyan";
    // 语调：0对应默认语速的1/2，100对应默认语速的2倍。最⼩值:0, 最⼤值:100
    public static final int PITCH = 50;
    // 语速：0对应默认语速的1/2，100对应默认语速的2倍。最⼩值:0, 最⼤值:100
    public static final int SPEED = 60;
    // 音量：0是静音，1对应默认音量1/2，100对应默认音量的2倍。最⼩值:0, 最⼤值:100
    public static int VOLUME = 60;
    public static int TTS_SYNTHESIS_STATUS_END = 2;
    public static final String AUDIO_DATA_KEY = "audio";
}
