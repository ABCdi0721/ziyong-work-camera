package com.workcamera.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 定位 + 逆地理编码，尽量取到「县/区 + 街道/小区」级地址；
 * 水印不使用经纬度，确实取不到地址时显示「位置未知」。
 * 全程不阻塞拍照流程（最多等待 timeoutMs）。
 */
class LocationHelper(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var cachedText: String? = null

    @Volatile
    private var cachedAt: Long = 0L

    /** 用户手动固定的地点（设置后优先使用，适合系统地址库取不到小区名的情况） */
    fun manualPlace(): String? {
        val saved = prefs.getString(KEY_MANUAL_PLACE, null)?.trim().orEmpty()
        return saved.ifEmpty { null }
    }

    fun setManualPlace(value: String?) {
        val trimmed = value?.trim().orEmpty()
        prefs.edit().apply {
            if (trimmed.isEmpty()) remove(KEY_MANUAL_PLACE) else putString(KEY_MANUAL_PLACE, trimmed)
        }.apply()
        cachedText = null
        cachedAt = 0L
    }

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    fun cached(): String? = cachedText

    /** 后台预热定位，结果用于相册与拍摄 */
    fun locateAsync(onResult: (String) -> Unit) {
        manualPlace()?.let {
            onResult(it)
            return
        }
        if (!hasPermission()) {
            onResult(context.getString(R.string.location_denied))
            return
        }
        Thread {
            val text = resolvePlace(4500)
            cache(text)
            mainHandler.post { onResult(text) }
        }.start()
    }

    /** 拍照时同步获取地点，带超时，避免水印长时间卡住 */
    fun currentPlaceBlocking(timeoutMs: Long = 5000): String {
        manualPlace()?.let { return it }
        if (!hasPermission()) return context.getString(R.string.location_denied)
        val cached = cachedText
        if (!cached.isNullOrBlank() && System.currentTimeMillis() - cachedAt < 120_000L) return cached
        val text = resolvePlace(timeoutMs)
        cache(text)
        return text
    }

    private fun cache(text: String) {
        cachedText = text
        cachedAt = System.currentTimeMillis()
    }

    private fun resolvePlace(timeoutMs: Long): String {
        manualPlace()?.let { return it }
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return context.getString(R.string.no_location)
        val location = requestFreshFix(manager, timeoutMs) ?: bestLastKnown(manager)
            ?: return context.getString(R.string.no_location)

        // 先用系统地址库；只有市/县不够精确时再查在线地名（无需密钥）
        val local = reverseGeocode(location.latitude, location.longitude)
        if (local != null && AddressFormatter.isDetailed(local)) return local

        val remote = RemoteGeocoder.lookup(location.latitude, location.longitude)
        return when {
            remote != null && AddressFormatter.isDetailed(remote) -> remote
            local != null -> local
            remote != null -> remote
            else -> context.getString(R.string.no_location)
        }
    }

    private fun requestFreshFix(manager: LocationManager, timeoutMs: Long): Location? {
        val latch = CountDownLatch(1)
        val holder = arrayOfNulls<Location>(1)
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (holder[0] == null) {
                    holder[0] = location
                    latch.countDown()
                }
            }
        }
        var registered = false
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            try {
                if (manager.isProviderEnabled(provider)) {
                    manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                    registered = true
                }
            } catch (e: Exception) {
            }
        }
        if (!registered) return null
        try {
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
        }
        try {
            manager.removeUpdates(listener)
        } catch (e: Exception) {
        }
        return holder[0]
    }

    private fun bestLastKnown(manager: LocationManager): Location? {
        var best: Location? = null
        for (provider in manager.allProviders) {
            try {
                val candidate = manager.getLastKnownLocation(provider) ?: continue
                val current = best
                if (current == null || candidate.time > current.time) {
                    best = candidate
                }
            } catch (e: Exception) {
            }
        }
        return best
    }

    private fun reverseGeocode(latitude: Double, longitude: Double): String? {
        if (!Geocoder.isPresent()) return null
        val addresses = queryAddresses(latitude, longitude)
        if (addresses.isEmpty()) return null

        val structured = addresses.mapNotNull { formatAddress(it) }
        if (structured.isNotEmpty()) {
            return structured.maxByOrNull { AddressFormatter.score(it) }
        }

        // 少数机型只返回整行地址，退化为去掉国名的完整地址
        return addresses.mapNotNull { it.getAddressLine(0) }
            .map { it.replace("中国", "").trim() }
            .filter { it.isNotEmpty() }
            .maxByOrNull { it.length }
    }

    private fun queryAddresses(latitude: Double, longitude: Double): List<Address> {
        var result = requestAddresses(latitude, longitude, Locale.SIMPLIFIED_CHINESE)
        if (result.isEmpty()) result = requestAddresses(latitude, longitude, Locale.getDefault())
        if (result.isEmpty()) result = requestAddresses(latitude, longitude, null)
        return result
    }

    @Suppress("DEPRECATION")
    private fun requestAddresses(latitude: Double, longitude: Double, locale: Locale?): List<Address> {
        return try {
            val geocoder = if (locale != null) Geocoder(context, locale) else Geocoder(context)
            geocoder.getFromLocation(latitude, longitude, 4).orEmpty()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 组装「县/区 + 街道/小区」级地址，例如 云和县小徐云亭小区。
     * 优先取县级行政区 + POI/小区名，其次街道名（必要时补门牌号）。
     */
    private fun formatAddress(address: Address): String? {
        val area = sequenceOf(address.subLocality, address.locality, address.subAdminArea, address.adminArea)
            .mapNotNull { it?.trim() }
            .firstOrNull { it.isNotEmpty() && it != "中国" }
        return AddressFormatter.compose(
            area = area,
            feature = address.featureName,
            street = address.thoroughfare,
            number = address.subThoroughfare
        )
    }

    companion object {
        private const val PREFS_NAME = "workcamera_settings"
        private const val KEY_MANUAL_PLACE = "manual_place"
    }
}
