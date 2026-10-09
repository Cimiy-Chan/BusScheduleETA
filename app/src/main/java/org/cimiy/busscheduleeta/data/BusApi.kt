package org.cimiy.busscheduleeta.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class RouteOption(
    val company: String,
    val route: String,
    val origTc: String,
    val destTc: String,
    val serviceType: String = "1"
)

data class StopOption(val seq: Int, val stopId: String, val nameTc: String)
data class EtaItem(val etaMillis: Long?, val remark: String)

object BusApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile private var cachedRoutes: List<RouteOption>? = null
    @Volatile private var kmbRouteRaw: JSONArray? = null
    @Volatile private var kmbStopNames: Map<String, String>? = null
    private val ctbStopNames = HashMap<String, String>()

    private fun getOnce(url: String): String =
        client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (resp.code == 403 || resp.code == 429) error("RATE:${resp.code}")
            if (!resp.isSuccessful) error("網絡錯誤 ${resp.code}")
            body
        }

    private suspend fun get(url: String): String {
        var last: Exception? = null
        repeat(4) { attempt ->
            try {
                return getOnce(url)
            } catch (e: Exception) {
                last = e
                if (e.message?.startsWith("RATE:") == true) {
                    delay(800L * (attempt + 1))
                } else throw e
            }
        }
        throw last ?: error("網絡錯誤")
    }

    private fun JSONObject.arr(key: String): JSONArray =
        optJSONArray(key) ?: JSONArray()

    suspend fun allRoutes(): List<RouteOption> = withContext(Dispatchers.IO) {
        cachedRoutes?.let { return@withContext it }
        val map = LinkedHashMap<String, RouteOption>()
        val kmb = JSONObject(get("https://data.etabus.gov.hk/v1/transport/kmb/route/")).arr("data")
        kmbRouteRaw = kmb
        for (i in 0 until kmb.length()) {
            val o = kmb.getJSONObject(i)
            val route = o.getString("route").trim()
            val key = "KMB|$route"
            if (key !in map) {
                map[key] = RouteOption(
                    "KMB", route, o.optString("orig_tc"), o.optString("dest_tc"),
                    o.optString("service_type", "1")
                )
            }
        }
        val ctb = JSONObject(get("https://rt.data.gov.hk/v2/transport/citybus/route/CTB")).arr("data")
        for (i in 0 until ctb.length()) {
            val o = ctb.getJSONObject(i)
            val route = o.getString("route").trim()
            val key = "CTB|$route"
            if (key !in map) {
                map[key] = RouteOption("CTB", route, o.optString("orig_tc"), o.optString("dest_tc"))
            }
        }
        map.values.sortedWith(compareBy({ it.route }, { it.company })).also { cachedRoutes = it }
    }

    suspend fun directions(company: String, route: String): Pair<RouteOption?, RouteOption?> =
        withContext(Dispatchers.IO) {
            when (company) {
                "KMB" -> {
                    val arr = kmbRouteRaw
                        ?: JSONObject(get("https://data.etabus.gov.hk/v1/transport/kmb/route/")).arr("data")
                            .also { kmbRouteRaw = it }
                    var out: RouteOption? = null
                    var inn: RouteOption? = null
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        if (o.getString("route").trim() != route) continue
                        val st = o.optString("service_type", "1")
                        val opt = RouteOption("KMB", route, o.optString("orig_tc"), o.optString("dest_tc"), st)
                        when (o.optString("bound")) {
                            "O" -> if (out == null) out = opt
                            "I" -> if (inn == null) inn = opt
                        }
                    }
                    out to inn
                }
                else -> {
                    val o = JSONObject(
                        get("https://rt.data.gov.hk/v2/transport/citybus/route/CTB/$route")
                    ).optJSONObject("data") ?: return@withContext null to null
                    val a = RouteOption("CTB", route, o.optString("orig_tc"), o.optString("dest_tc"))
                    a to a.copy(origTc = a.destTc, destTc = a.origTc)
                }
            }
        }

    private suspend fun kmbStopsMap(): Map<String, String> {
        kmbStopNames?.let { return it }
        val map = HashMap<String, String>()
        val allStops = JSONObject(get("https://data.etabus.gov.hk/v1/transport/kmb/stop")).arr("data")
        for (i in 0 until allStops.length()) {
            val s = allStops.getJSONObject(i)
            map[s.getString("stop").trim()] = s.optString("name_tc")
        }
        kmbStopNames = map
        return map
    }

    private suspend fun ctbStopName(stopId: String): String {
        ctbStopNames[stopId]?.let { return it }
        delay(120)
        val name = JSONObject(get("https://rt.data.gov.hk/v2/transport/citybus/stop/$stopId"))
            .optJSONObject("data")?.optString("name_tc") ?: stopId
        ctbStopNames[stopId] = name
        return name
    }

    suspend fun stops(company: String, route: String, direction: String, serviceType: String): List<StopOption> =
        withContext(Dispatchers.IO) {
            val dirPath = if (direction == "O") "outbound" else "inbound"
            when (company) {
                "KMB" -> {
                    val stopMap = kmbStopsMap()
                    val arr = JSONObject(
                        get("https://data.etabus.gov.hk/v1/transport/kmb/route-stop/$route/$dirPath/$serviceType")
                    ).arr("data")
                    buildList {
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            val id = o.getString("stop").trim()
                            add(StopOption(o.getInt("seq"), id, stopMap[id] ?: id))
                        }
                    }
                }
                else -> {
                    val arr = JSONObject(
                        get("https://rt.data.gov.hk/v2/transport/citybus/route-stop/CTB/$route/$dirPath")
                    ).arr("data")
                    buildList {
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            val id = o.getString("stop").trim()
                            add(StopOption(o.getInt("seq"), id, ctbStopName(id)))
                        }
                    }
                }
            }
        }
    suspend fun eta(
        company: String,
        stopId: String,
        route: String,
        direction: String,
        serviceType: String
    ): List<EtaItem> = withContext(Dispatchers.IO) {
        val url = when (company) {
            "KMB" -> "https://data.etabus.gov.hk/v1/transport/kmb/eta/$stopId/$route/$serviceType"
            else -> "https://rt.data.gov.hk/v2/transport/citybus/eta/CTB/$stopId/$route"
        }
        val arr = JSONObject(get(url)).arr("data")
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (direction.isNotEmpty() && o.optString("dir") != direction) continue
                val etaStr = o.optString("eta", "")
                val millis = if (etaStr.isBlank() || etaStr == "null") null
                else runCatching {
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US)
                    sdf.parse(etaStr)?.time
                }.getOrNull()
                add(EtaItem(millis, o.optString("rmk_tc")))
            }
        }.take(3)
    }
}