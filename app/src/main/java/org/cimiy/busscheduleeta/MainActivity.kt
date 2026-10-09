package org.cimiy.busscheduleeta

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import org.cimiy.busscheduleeta.data.FavoriteTrip
import org.cimiy.busscheduleeta.databinding.ActivityMainBinding
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.cimiy.busscheduleeta.data.AppDatabase

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: FavoriteAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.mainRoot) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(16 + bars.left, 16 + bars.top, 16 + bars.right, 16 + bars.bottom)
            insets
        }

        adapter = FavoriteAdapter(
            onClick = { trip ->
                startActivity(
                    Intent(this, EtaActivity::class.java)
                        .putExtra(EtaActivity.EXTRA_ROUTE, trip.routeLabel)
                        .putExtra(EtaActivity.EXTRA_DEST, trip.destLabel)
                        .putExtra(EtaActivity.EXTRA_STOP, trip.stopLabel)
                        .putExtra("company", trip.company)
                        .putExtra("direction", trip.direction)
                        .putExtra("service_type", trip.serviceType)
                        .putExtra("stop_id", trip.stopId)
                )
            },            onLongClick = { /* 第 4 項才做刪除 */ }
        )
        binding.rvFavorites.layoutManager = GridLayoutManager(this, 3)
        binding.rvFavorites.adapter = adapter

        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, PickRouteActivity::class.java))
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppDatabase.get(this@MainActivity).favoriteDao().all().collect { list ->
                    adapter.submit(list)
                    binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                    binding.fabAdd.visibility = if (list.size >= 9) View.GONE else View.VISIBLE
                }
            }
        }

        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, PickRouteActivity::class.java))
        }
    }
}