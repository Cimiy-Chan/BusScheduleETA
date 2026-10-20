package org.cimiy.busscheduleeta

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.cimiy.busscheduleeta.data.BusApi
import org.cimiy.busscheduleeta.data.EtaItem
import org.cimiy.busscheduleeta.databinding.ActivityEtaBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EtaActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEtaBinding
    private var refreshJob: Job? = null

    private val company by lazy { intent.getStringExtra(EXTRA_COMPANY).orEmpty() }
    private val direction by lazy { intent.getStringExtra(EXTRA_DIRECTION).orEmpty() }
    private val serviceType by lazy { intent.getStringExtra(EXTRA_SERVICE_TYPE) ?: "1" }
    private val stopId by lazy { intent.getStringExtra(EXTRA_STOP_ID).orEmpty() }
    private val routeNo by lazy { intent.getStringExtra(EXTRA_ROUTE).orEmpty() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityEtaBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.etaRoot) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(16 + bars.left, 16 + bars.top, 16 + bars.right, 16 + bars.bottom)
            insets
        }

        binding.tvRouteTitle.text = "$routeNo 往${intent.getStringExtra(EXTRA_DEST).orEmpty()}"
        binding.tvStopName.text = "上車站：${intent.getStringExtra(EXTRA_STOP).orEmpty()}"
        binding.btnBack.setOnClickListener { finish() }

        // 先清掉版面預設的「3 分鐘」，避免閃一下
        binding.tvMinutes.text = "--"
        binding.tvMinutesUnit.text = "分鐘"
        binding.tvClock.text = getString(R.string.loading)
        binding.tvSecond.text = ""
        binding.tvThird.text = ""
        binding.tvRemark.visibility = View.GONE
        binding.tvMessage.visibility = View.GONE
    }

    override fun onStart() {
        refreshJob?.cancel()
        super.onStart()
        refreshJob = lifecycleScope.launch {
            while (isActive) {
                loadEta()
                delay(30_000)
            }
        }
    }

    override fun onStop() {
        refreshJob?.cancel()
        refreshJob = null
        super.onStop()
    }

    private suspend fun loadEta() {
        try {
            var list = BusApi.eta(company, stopId, routeNo, direction, serviceType)
            // 若按方向篩完是空的，退一步用不篩方向的結果（避免方向欄位不一致）
            if (list.isEmpty()) {
                list = BusApi.eta(company, stopId, routeNo, direction = "", serviceType)
            }
            
            runOnUiThread { render(list) }
        } catch (e: Exception) {
            runOnUiThread {
                showMessage("請檢查網絡\n${e.message}")
                binding.tvUpdated.text = "最後更新：失敗"
            }
        }
    }

    private fun render(list: List<EtaItem>) {
        val now = System.currentTimeMillis()
        val clock = SimpleDateFormat("HH:mm", Locale.US)
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.US)
        binding.tvUpdated.text = "最後更新：${stamp.format(Date())}"

        val valid = list.filter { it.etaMillis != null && it.etaMillis!! >= now - 60_000 }
        if (valid.isEmpty()) {
            // 備註不可當作主訊息（「原定班次」只是說明）
            val remark = list.firstOrNull { it.remark.isNotBlank() }?.remark
            showMessage("暫時沒有班次資料")
            if (!remark.isNullOrBlank()) {
                binding.tvRemark.text = remark
                binding.tvRemark.visibility = View.VISIBLE
            }
            return
        }

        binding.tvMessage.visibility = View.GONE
        binding.tvNextLabel.visibility = View.VISIBLE
        binding.tvMinutes.visibility = View.VISIBLE
        binding.tvMinutesUnit.visibility = View.VISIBLE
        binding.tvClock.visibility = View.VISIBLE
        binding.divider.visibility = View.VISIBLE

        fun rowLabel(ms: Long): String {
            val m = ((ms - now) / 60_000L).toInt()
            return if (m < 1) "即將到站" else "$m 分鐘"
        }

        val firstMs = valid[0].etaMillis!!
        if (firstMs - now < 60_000) {
            binding.tvMinutes.text = "即將到站"
            binding.tvMinutes.textSize = 40f
            binding.tvMinutesUnit.visibility = View.GONE
        } else {
            binding.tvMinutes.text = ((firstMs - now) / 60_000L).toInt().toString()
            binding.tvMinutes.textSize = 96f
            binding.tvMinutesUnit.visibility = View.VISIBLE
            binding.tvMinutesUnit.text = "分鐘"
        }
        binding.tvClock.text = "約 ${clock.format(Date(firstMs))}"
        val remark = valid[0].remark
        binding.tvRemark.text = remark
        binding.tvRemark.visibility = if (remark.isBlank()) View.GONE else View.VISIBLE

        binding.tvSecond.visibility = if (valid.size >= 2) View.VISIBLE else View.GONE
        if (valid.size >= 2) {
            val ms = valid[1].etaMillis!!
            binding.tvSecond.text = "第 2 班　${rowLabel(ms)}　約 ${clock.format(Date(ms))}"
        }
        binding.tvThird.visibility = if (valid.size >= 3) View.VISIBLE else View.GONE
        if (valid.size >= 3) {
            val ms = valid[2].etaMillis!!
            binding.tvThird.text = "第 3 班　${rowLabel(ms)}　約 ${clock.format(Date(ms))}"
        }
    }


    private fun showMessage(msg: String) {
        binding.tvMinutes.textSize = 96f
        binding.tvMinutesUnit.visibility = View.VISIBLE
        binding.tvMessage.text = msg
        binding.tvMessage.visibility = View.VISIBLE
        binding.tvNextLabel.visibility = View.GONE
        binding.tvMinutes.visibility = View.GONE
        binding.tvMinutesUnit.visibility = View.GONE
        binding.tvClock.visibility = View.GONE
        binding.divider.visibility = View.GONE
        binding.tvSecond.visibility = View.GONE
        binding.tvThird.visibility = View.GONE
        binding.tvRemark.visibility = View.GONE
    }

    companion object {
        const val EXTRA_ROUTE = "route"
        const val EXTRA_DEST = "dest"
        const val EXTRA_STOP = "stop"
        const val EXTRA_COMPANY = "company"
        const val EXTRA_DIRECTION = "direction"
        const val EXTRA_SERVICE_TYPE = "service_type"
        const val EXTRA_STOP_ID = "stop_id"
    }
}