package com.cornex.voicekit.api

/**
 * ASR能力初始化结果接口类
 * @author deng
 * @version 1.0.0
 * @since 2026/5/21
 */
interface IInitResult {
    /**
     * 初始化成功
     */
    fun onSuccess()

    /**
     * 初始化失败
     * @param errorMsg 错误信息
     */
    fun onFail(errorMsg: String?)
}
