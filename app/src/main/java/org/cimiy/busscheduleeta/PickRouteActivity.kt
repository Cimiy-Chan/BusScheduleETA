package org.cimiy.busscheduleeta

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import org.cimiy.busscheduleeta.data.BusApi
import org.cimiy.busscheduleeta.databinding.ActivityPickRouteBinding

class PickRouteActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPickRouteBinding
    private lateinit var adapter: RouteAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityPickRouteBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.pickRouteRoot) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(16 + bars.left, 16 + bars.top, 16 + bars.right, 16 + bars.bottom)
            insets
        }

        adapter = RouteAdapter { route ->
            startActivity(
                Intent(this, PickDirectionActivity::class.java)
                    .putExtra(EXTRA_COMPANY, route.company)
                    .putExtra(EXTRA_ROUTE, route.route)
            )
        }
        binding.rvRoutes.layoutManager = LinearLayoutManager(this)
        binding.rvRoutes.adapter = adapter
        binding.btnBack.setOnClickListener { finish() }
        binding.etSearch.doAfterTextChanged { adapter.filter(it?.toString().orEmpty()) }
        binding.tvError.visibility = View.GONE
        binding.loadingRoutes.visibility = View.VISIBLE

        //Toast.makeText(this, "正在下載路線…", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                adapter.submit(BusApi.allRoutes())
                binding.tvError.visibility = View.GONE
                Toast.makeText(this@PickRouteActivity, "可以搜尋路線號碼", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                val full = buildString {
                    append(e.javaClass.simpleName).append("：").append(e.message)
                    var c = e.cause
                    while (c != null) {
                        append("\n→ ").append(c.javaClass.simpleName).append("：").append(c.message)
                        c = c.cause
                    }
                }
                binding.tvError.text = "下載失敗\n$full"
                binding.tvError.visibility = View.VISIBLE
            } finally {
                binding.loadingRoutes.visibility = View.GONE
            }
        }
    }

    companion object {
        const val EXTRA_COMPANY = "company"
        const val EXTRA_ROUTE = "route"
    }
}