package com.workcamera.app

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * 轻量索引：文件名 -> 拍摄地点。
 * 相册按地点搜索依赖它；即使索引丢失，照片本身仍然在「下载/工作相机」里。
 */
object PhotoIndex {

    private var file: File? = null
    private val map = HashMap<String, String>()

    @Synchronized
    private fun ensure(context: Context) {
        if (file != null) return
        val f = File(context.filesDir, "photo_index.json")
        file = f
        try {
            if (f.exists()) {
                val json = JSONObject(f.readText(Charsets.UTF_8))
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    map[key] = json.optString(key, "")
                }
            }
        } catch (e: Exception) {
            // 索引损坏不影响拍照与浏览
        }
    }

    @Synchronized
    fun put(context: Context, fileName: String, location: String) {
        ensure(context)
        map[fileName] = location
        persist()
    }

    @Synchronized
    fun locationOf(context: Context, fileName: String): String? {
        ensure(context)
        return map[fileName]
    }

    @Synchronized
    fun remove(context: Context, fileName: String) {
        ensure(context)
        if (map.remove(fileName) != null) persist()
    }

    private fun persist() {
        val f = file ?: return
        try {
            val json = JSONObject()
            for ((k, v) in map) json.put(k, v)
            val tmp = File(f.parentFile, "photo_index.tmp")
            tmp.writeText(json.toString(), Charsets.UTF_8)
            if (!tmp.renameTo(f)) {
                f.writeText(json.toString(), Charsets.UTF_8)
                tmp.delete()
            }
        } catch (e: Exception) {
            // 忽略写入失败
        }
    }
}
