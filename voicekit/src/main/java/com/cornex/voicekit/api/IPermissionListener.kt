package com.cornex.voicekit.api

/**
 * 权限申请结果回调接口
 * @author deng
 * @version 1.0.0
 * @since 2026/5/21
 */
interface IPermissionListener {
    /**
     * 有权限被同意授予时回调
     * 
     * @param granted  请求成功的权限组
     * @param all 是否全部授予了 true 全部授予,否则为false
     */
    fun hasPermission(granted: MutableList<String?>?, all: Boolean)

    /**
     * 有权限被拒绝授予时回调
     * 
     * @param denied    请求失败的权限组
     * @param quick 是否有某个权限被永久拒绝了 true 拒绝,否则为false
     */
    fun noPermission(denied: MutableList<String?>?, quick: Boolean)
}
