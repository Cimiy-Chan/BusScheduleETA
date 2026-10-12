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
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.cimiy.busscheduleeta.data.AppDatabase
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.widget.PopupMenu
import androidx.appcompat.app.AppCompatDelegate



class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: FavoriteAdapter

    companion object {
        private const val MAX_FAVORITE = 12
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        applySavedTheme()
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
            },
            onLongClick = { trip ->
                MaterialAlertDialogBuilder(this)
                    .setTitle("刪除常用行程？")
                    .setMessage("${trip.routeLabel} 往${trip.destLabel}\n上車站：${trip.stopLabel}")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("刪除") { _, _ ->
                        lifecycleScope.launch {
                            AppDatabase.get(this@MainActivity).favoriteDao().delete(trip)
                        }
                    }
                    .show()
                    .also { dialog ->
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                            ?.setTextColor(getColor(R.color.error_text))
                    }
            }
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
                    binding.fabAdd.visibility = if (list.size >= MAX_FAVORITE) View.GONE else View.VISIBLE
                }
            }
        }

        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, PickRouteActivity::class.java))
        }

        binding.btnSettings.setOnClickListener { view ->
            PopupMenu(this, view).apply {
                menuInflater.inflate(R.menu.menu_main, menu)
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_about -> {
                            showAboutDialog()
                            true
                        }
                        R.id.action_appearance -> {
                            showAppearanceDialog()
                            true
                        }
                        else -> false
                    }
                }
                show()
            }
        }

    }

    private fun showAboutDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_about, null)
        AlertDialog.Builder(this)
            .setTitle(R.string.about)
            .setView(view)
            .setPositiveButton(R.string.about_ok, null)
            .show()
    }
    private val prefs by lazy {
        getSharedPreferences("settings", MODE_PRIVATE)
    }

    private fun applySavedTheme() {
        val mode = prefs.getInt("night_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    private fun showAppearanceDialog() {
        val labels = arrayOf(
            getString(R.string.theme_light),
            getString(R.string.theme_dark),
            getString(R.string.theme_system)
        )
        val modes = intArrayOf(
            AppCompatDelegate.MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_YES,
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        )
        val current = prefs.getInt("night_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        val checked = modes.indexOf(current).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(R.string.appearance)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                val mode = modes[which]
                prefs.edit().putInt("night_mode", mode).apply()
                AppCompatDelegate.setDefaultNightMode(mode)
                dialog.dismiss()
                recreate()
            }
            .show()
    }
}