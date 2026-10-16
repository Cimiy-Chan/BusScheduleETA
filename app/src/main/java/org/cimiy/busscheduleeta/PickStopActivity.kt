package org.cimiy.busscheduleeta

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import org.cimiy.busscheduleeta.data.AppDatabase
import org.cimiy.busscheduleeta.data.BusApi
import org.cimiy.busscheduleeta.data.FavoriteTrip
import org.cimiy.busscheduleeta.databinding.ActivityPickStopBinding
import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.pow

class PickStopActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPickStopBinding
    private lateinit var adapter: StopAdapter

    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
    private var baseStops: List<org.cimiy.busscheduleeta.data.StopOption> = emptyList()
    private val locPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val ok = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (ok) trySortByNearby() else keepManualList()
    }
    companion object {
        private const val MAX_FAVORITES = 12
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityPickStopBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.pickStopRoot) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(16 + bars.left, 16 + bars.top, 16 + bars.right, 16 + bars.bottom)
            insets
        }

        val company = intent.getStringExtra(PickRouteActivity.EXTRA_COMPANY).orEmpty()
        val route = intent.getStringExtra(PickRouteActivity.EXTRA_ROUTE).orEmpty()
        val direction = intent.getStringExtra(PickDirectionActivity.EXTRA_DIRECTION).orEmpty()
        val serviceType = intent.getStringExtra(PickDirectionActivity.EXTRA_SERVICE_TYPE) ?: "1"
        val dest = intent.getStringExtra(PickDirectionActivity.EXTRA_DEST).orEmpty()

        binding.tvTitle.text = "選擇上車站"
        binding.tvHint.text = "$route 往$dest　正在載入車站…"
        binding.btnBack.setOnClickListener { finish() }

        adapter = StopAdapter { stop ->
            lifecycleScope.launch {
                val dao = AppDatabase.get(this@PickStopActivity).favoriteDao()
                if (dao.count() >= MAX_FAVORITES) {
                    Toast.makeText(this@PickStopActivity, "最多只能加 12 個常用行程", Toast.LENGTH_LONG).show()
                    return@launch
                }
                dao.insert(
                    FavoriteTrip(
                        company = company,
                        route = route,
                        direction = direction,
                        serviceType = serviceType,
                        stopId = stop.stopId,
                        routeLabel = route,
                        destLabel = dest,
                        stopLabel = stop.nameTc,
                        sortOrder = dao.count()
                    )
                )
                AppToast.show(this@PickStopActivity, "已加入常用", R.drawable.ic_toast_ok)
                startActivity(
                    Intent(this@PickStopActivity, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
                finish()
            }
        }
        binding.rvStops.layoutManager = LinearLayoutManager(this)
        binding.rvStops.adapter = adapter

        lifecycleScope.launch {
            try {
                val list = BusApi.stops(company, route, direction, serviceType)
                adapter.submit(list)
                binding.tvHint.text = "$route 往$dest　共 ${list.size} 個站"
            } catch (e: Exception) {
                binding.tvHint.text = "載入失敗：${e.message}"
                AppToast.show(this@PickStopActivity, "載入失敗：${e.message}", R.drawable.ic_toast_warn)
            }
        }
    }
    private fun isAutoLocation() =
        prefs.getString("location_mode", "manual") == "auto"

    private fun applyStops(list: List<org.cimiy.busscheduleeta.data.StopOption>) {
        baseStops = list
        if (!isAutoLocation()) {
            adapter.submit(list)
            binding.tvLocationHint.visibility = View.GONE
            return
        }
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            trySortByNearby()
        } else {
            locPermission.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun keepManualList() {
        adapter.submit(baseStops)
        binding.tvLocationHint.visibility = View.GONE
    }
    private fun trySortByNearby() {
        val loc = lastKnownLocation()
        if (loc == null) {
            keepManualList()
            return
        }
        val sorted = baseStops.sortedBy { stop ->
            if (stop.lat.isNaN() || stop.lng.isNaN()) Double.MAX_VALUE
            else distanceMeters(loc.latitude, loc.longitude, stop.lat, stop.lng)
        }
        adapter.submit(sorted)
        binding.tvLocationHint.visibility = View.VISIBLE
    }

    private fun lastKnownLocation(): Location? {
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { p ->
                try { lm.getLastKnownLocation(p) } catch (_: SecurityException) { null }
            }
            .maxByOrNull { it.time }
    }
    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2).pow(2) +
                kotlin.math.cos(Math.toRadians(lat1)) *
                kotlin.math.cos(Math.toRadians(lat2)) *
                kotlin.math.sin(dLon / 2).pow(2)
        return 2 * r * kotlin.math.asin(kotlin.math.sqrt(a))
    }
}