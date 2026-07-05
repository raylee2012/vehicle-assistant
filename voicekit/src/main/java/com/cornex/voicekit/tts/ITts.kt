package com.cornex.voicekit.tts

/**
 * TTS合并并播报能力接口
 * @author deng
 * @version 1.0.0
 * @since 2026/5/26
 */
interface ITts {
    /**
     * 初始化TTS能力
     */
    fun initTts()

    /**
     * 开始合成并播放
     */
    fun startPlay(ttsText: String)

    /**
     * 流式送入文本合成并播报，适合长文本逐段下发场景
     * @param ttsText 需要送入队列的文本片段
     */
    fun feedTextPlay(ttsText: String)

    /**
     * 停止播报
     */
    fun stopPlay()

    /**
     * 设置Tts合成结果监听器
     * @param listener Tts合成结果监听器
     */
    fun setOnTtsResultListener(listener: ITtsSynthesisListener?)

    /**
     * 释放资源
     */
    fun dispose()
}