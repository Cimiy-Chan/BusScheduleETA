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

class PickStopActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPickStopBinding
    private lateinit var adapter: StopAdapter

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
                if (dao.count() >= 9) {
                    Toast.makeText(this@PickStopActivity, "最多只能加 9 個常用行程", Toast.LENGTH_LONG).show()
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
                Toast.makeText(this@PickStopActivity, "已加入常用", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this@PickStopActivity, "載入失敗：${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}