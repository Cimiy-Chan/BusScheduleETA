package org.cimiy.busscheduleeta

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.cimiy.busscheduleeta.data.BusApi
import org.cimiy.busscheduleeta.data.RouteOption
import org.cimiy.busscheduleeta.databinding.ActivityPickDirectionBinding

class PickDirectionActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPickDirectionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityPickDirectionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.pickDirectionRoot) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(16 + bars.left, 16 + bars.top, 16 + bars.right, 16 + bars.bottom)
            insets
        }

        val company = intent.getStringExtra(PickRouteActivity.EXTRA_COMPANY).orEmpty()
        val route = intent.getStringExtra(PickRouteActivity.EXTRA_ROUTE).orEmpty()
        binding.tvTitle.text = "$route　選擇方向"
        binding.btnBack.setOnClickListener { finish() }
        binding.btnOutbound.isEnabled = false
        binding.btnInbound.isEnabled = false
        binding.btnOutbound.text = "載入中…"
        binding.btnInbound.text = "載入中…"

        lifecycleScope.launch {
            try {
                val (out, inn) = BusApi.directions(company, route)
                bindDir(binding.btnOutbound, out, "O")
                bindDir(binding.btnInbound, inn, "I")
                if (out == null && inn == null) {
                    Toast.makeText(this@PickDirectionActivity, "找不到方向資料", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@PickDirectionActivity, "載入失敗：${e.message}", Toast.LENGTH_LONG).show()
                binding.btnOutbound.text = "載入失敗"
                binding.btnInbound.visibility = View.GONE
            }
        }
    }

    private fun bindDir(btn: com.google.android.material.button.MaterialButton, opt: RouteOption?, dir: String) {
        if (opt == null) {
            btn.visibility = View.GONE
            return
        }
        btn.visibility = View.VISIBLE
        btn.isEnabled = true
        btn.text = "往 ${opt.destTc}"
        btn.setOnClickListener {
            startActivity(
                Intent(this, PickStopActivity::class.java)
                    .putExtra(PickRouteActivity.EXTRA_COMPANY, opt.company)
                    .putExtra(PickRouteActivity.EXTRA_ROUTE, opt.route)
                    .putExtra(EXTRA_DIRECTION, dir)
                    .putExtra(EXTRA_SERVICE_TYPE, opt.serviceType)
                    .putExtra(EXTRA_DEST, opt.destTc)
            )
        }
    }

    companion object {
        const val EXTRA_DIRECTION = "direction"
        const val EXTRA_SERVICE_TYPE = "service_type"
        const val EXTRA_DEST = "dest"
    }
}