package org.cimiy.busscheduleeta

import android.content.Intent
import android.os.Bundle
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
import kotlin.math.pow

class PickStopActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPickStopBinding
    private lateinit var adapter: StopAdapter
    private var baseStops: List<org.cimiy.busscheduleeta.data.StopOption> = emptyList()
    private val locPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val ok = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (ok) tryAutoSelectNearest() else keepManualList()
    }

    private var company = ""
    private var route = ""
    private var direction = ""
    private var serviceType = "1"
    private var dest = ""

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

        company = intent.getStringExtra(PickRouteActivity.EXTRA_COMPANY).orEmpty()
        route = intent.getStringExtra(PickRouteActivity.EXTRA_ROUTE).orEmpty()
        direction = intent.getStringExtra(PickDirectionActivity.EXTRA_DIRECTION).orEmpty()
        serviceType = intent.getStringExtra(PickDirectionActivity.EXTRA_SERVICE_TYPE) ?: "1"
        dest = intent.getStringExtra(PickDirectionActivity.EXTRA_DEST).orEmpty()

        binding.tvTitle.text = "選擇上車站"
        binding.tvHint.text = "$route 往$dest　正在載入車站…"
        binding.btnBack.setOnClickListener { finish() }
        binding.btnFindNearest.setOnClickListener { requestNearestStop() }

        adapter = StopAdapter { stop -> selectStop(stop)}
        binding.rvStops.layoutManager = LinearLayoutManager(this)
        binding.rvStops.adapter = adapter

        lifecycleScope.launch {
            try {
                val list = BusApi.stops(company, route, direction, serviceType)
                applyStops(list)
                binding.tvHint.text = "$route 往$dest　共 ${list.size} 個站"
            } catch (e: Exception) {
                binding.tvHint.text = "載入失敗：${e.message}"
                AppToast.show(this@PickStopActivity, "載入失敗：${e.message}", R.drawable.ic_toast_warn)
            }
        }
    }
    private fun applyStops(list: List<org.cimiy.busscheduleeta.data.StopOption>) {
        baseStops = list
        adapter.submit(list)
        binding.rvStops.visibility = View.VISIBLE
        binding.tvLocationHint.visibility = View.GONE
    }

    private fun requestNearestStop() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            tryAutoSelectNearest()
        } else {
            locPermission.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }
    private fun selectStop(
        stop: org.cimiy.busscheduleeta.data.StopOption,
        fromAuto: Boolean = false
    ) {
        lifecycleScope.launch {
            val dao = AppDatabase.get(this@PickStopActivity).favoriteDao()
            if (dao.count() >= AppConstants.MAX_FAVORITES) {
                AppToast.show(this@PickStopActivity, getString(R.string.favorites_full), R.drawable.ic_toast_warn)
                keepManualList()
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
            val msg = if (fromAuto) {
                getString(R.string.location_auto_selected, stop.nameTc)
            } else {
                getString(R.string.added_favorite)
            }
            AppToast.show(this@PickStopActivity, msg, R.drawable.ic_toast_ok)
            startActivity(
                Intent(this@PickStopActivity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
            finish()
        }
    }
    private fun tryAutoSelectNearest() {
        val loc = lastKnownLocation()
        if (loc == null) {
            keepManualList()
            AppToast.show(this, getString(R.string.location_permission_needed), R.drawable.ic_toast_warn)
            return
        }
        val nearest = baseStops
            .filter { !it.lat.isNaN() && !it.lng.isNaN() }
            .minByOrNull { distanceMeters(loc.latitude, loc.longitude, it.lat, it.lng) }
        if (nearest == null) {
            keepManualList()
            return
        }
        // 這裡 nearest 已保證非 null
        binding.rvStops.visibility = View.GONE
        binding.tvLocationHint.visibility = View.GONE
        selectStop(nearest, fromAuto = true)
    }

    private fun keepManualList() {
        adapter.submit(baseStops)
        binding.tvLocationHint.visibility = View.GONE
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