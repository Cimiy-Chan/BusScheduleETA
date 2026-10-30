package org.cimiy.busscheduleeta

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.view.ViewPropertyAnimator
import android.widget.PopupMenu
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import org.cimiy.busscheduleeta.data.AppDatabase
import org.cimiy.busscheduleeta.data.FavoriteTrip
import org.cimiy.busscheduleeta.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: FavoriteAdapter

    private var rvPaddingBottomPx: Int = 0
    private var deleteZoneSlidePx: Float = 0f

    private var favoritesItemAnimator: RecyclerView.ItemAnimator? = null
    private var zoneAnimator: ViewPropertyAnimator? = null
    private var zoneHovered: Boolean = false
    private var droppedOnDeleteZone: Boolean = false
    private var pendingDeleteTrip: FavoriteTrip? = null
    /** 刪除區是否正以 alpha 顯示（永遠 VISIBLE，勿用 GONE） */
    private var deleteZoneRevealed: Boolean = false
    private var fabWasInteractiveBeforeDrag: Boolean = true
    private var dragStartAdapterPosition: Int = RecyclerView.NO_POSITION

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        applySavedTheme()
        setContentView(binding.root)

        val density = resources.displayMetrics.density
        rvPaddingBottomPx = (128 * density).toInt()
        deleteZoneSlidePx = 8f * density

        // 待機：alpha=0、不可點；勿設 GONE
        binding.deleteZone.alpha = 0f
        binding.deleteZone.visibility = View.VISIBLE
        binding.deleteZone.isClickable = false
        binding.deleteZone.isFocusable = false
        binding.deleteZone.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        binding.deleteZone.elevation = 1f * density

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
                        .putExtra(EtaActivity.EXTRA_COMPANY, trip.company)
                        .putExtra(EtaActivity.EXTRA_DIRECTION, trip.direction)
                        .putExtra(EtaActivity.EXTRA_SERVICE_TYPE, trip.serviceType)
                        .putExtra(EtaActivity.EXTRA_STOP_ID, trip.stopId)
                )
            }
        )
        binding.rvFavorites.layoutManager = GridLayoutManager(this, 3)
        binding.rvFavorites.adapter = adapter
        binding.rvFavorites.setPadding(
            binding.rvFavorites.paddingLeft,
            binding.rvFavorites.paddingTop,
            binding.rvFavorites.paddingRight,
            rvPaddingBottomPx
        )
        attachItemTouchHelper()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppDatabase.get(this@MainActivity).favoriteDao().all().collect { list ->
                    adapter.submit(list)
                    val shown = adapter.currentItems()
                    binding.tvEmpty.visibility =
                        if (shown.isEmpty()) View.VISIBLE else View.GONE
                    if (!deleteZoneRevealed) {
                        applyFabAvailability(shown.size)
                    }
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

        maybeLaunchEtaFromIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        maybeLaunchEtaFromIntent(intent)
    }

    private fun maybeLaunchEtaFromIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_ETA, false) != true) return
        intent.removeExtra(EXTRA_OPEN_ETA)
        startActivity(
            Intent(this, EtaActivity::class.java)
                .putExtra(EtaActivity.EXTRA_ROUTE, intent.getStringExtra(EtaActivity.EXTRA_ROUTE))
                .putExtra(EtaActivity.EXTRA_DEST, intent.getStringExtra(EtaActivity.EXTRA_DEST))
                .putExtra(EtaActivity.EXTRA_STOP, intent.getStringExtra(EtaActivity.EXTRA_STOP))
                .putExtra(EtaActivity.EXTRA_COMPANY, intent.getStringExtra(EtaActivity.EXTRA_COMPANY))
                .putExtra(EtaActivity.EXTRA_DIRECTION, intent.getStringExtra(EtaActivity.EXTRA_DIRECTION))
                .putExtra(
                    EtaActivity.EXTRA_SERVICE_TYPE,
                    intent.getStringExtra(EtaActivity.EXTRA_SERVICE_TYPE) ?: "1"
                )
                .putExtra(EtaActivity.EXTRA_STOP_ID, intent.getStringExtra(EtaActivity.EXTRA_STOP_ID))
        )
    }

    private fun attachItemTouchHelper() {
        val helper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or
                    ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
            0
        ) {
            override fun isLongPressDragEnabled(): Boolean = true
            override fun isItemViewSwipeEnabled(): Boolean = false

            override fun getMoveThreshold(viewHolder: RecyclerView.ViewHolder): Float = 0.2f

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                return adapter.moveItem(from, to)
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                when (actionState) {
                    ItemTouchHelper.ACTION_STATE_DRAG -> {
                        viewHolder?.itemView?.isPressed = false
                        droppedOnDeleteZone = false
                        dragStartAdapterPosition = viewHolder?.bindingAdapterPosition
                            ?: RecyclerView.NO_POSITION
                        pendingDeleteTrip = dragStartAdapterPosition
                            .takeIf { it != RecyclerView.NO_POSITION }
                            ?.let { adapter.itemAt(it) }
                        adapter.beginDrag()
                        if (favoritesItemAnimator == null) {
                            favoritesItemAnimator = binding.rvFavorites.itemAnimator
                            binding.rvFavorites.itemAnimator = null
                        }
                        // A: 不改 VISIBILITY／padding／RV elevation／bringToFront
                        showDeleteZone()
                        applyDragCardElevation(viewHolder?.itemView)
                    }
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                // 刪除：旗標或目前重疊任一即可（勿要求 AND；鬆手瞬間座標易抖）
                val overDelete =
                    droppedOnDeleteZone || isViewOverDeleteZone(viewHolder.itemView)
                val toDelete = pendingDeleteTrip
                val startPos = dragStartAdapterPosition

                // 投刪除區：略過換位補位，避免還原／重排蓋掉刪除路徑
                if (!overDelete) {
                    reconcileDropIfNeeded(recyclerView, viewHolder, startPos)
                }

                viewHolder.itemView.translationX = 0f
                viewHolder.itemView.translationY = 0f
                viewHolder.itemView.scaleX = 1f
                viewHolder.itemView.scaleY = 1f
                viewHolder.itemView.alpha = 1f
                resetDragCardElevation(viewHolder.itemView)

                val ordered = adapter.finishDrag(droppedOnDelete = overDelete)
                pendingDeleteTrip = null
                droppedOnDeleteZone = false
                dragStartAdapterPosition = RecyclerView.NO_POSITION

                hideDeleteZone()
                setDeleteZoneHovered(false)

                if (overDelete && toDelete != null) {
                    confirmDeleteFavorite(toDelete)
                } else if (ordered != null) {
                    lifecycleScope.launch {
                        AppDatabase.get(this@MainActivity)
                            .persistFavoriteSortOrders(ordered)
                    }
                }

                val pendingAnimator = favoritesItemAnimator
                if (pendingAnimator != null) {
                    favoritesItemAnimator = null
                    recyclerView.post {
                        if (recyclerView.itemAnimator == null) {
                            recyclerView.itemAnimator = pendingAnimator
                        }
                    }
                }
            }

            override fun onChildDraw(
                c: android.graphics.Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder.itemView.translationX = dX
                    viewHolder.itemView.translationY = dY
                    applyDragCardElevation(viewHolder.itemView)

                    val over = isViewOverDeleteZone(viewHolder.itemView)
                    if (isCurrentlyActive) {
                        droppedOnDeleteZone = over
                    }
                    // 疊上刪除區：卡＋區半透明，提示即將刪除
                    applyDeleteOverlapStyle(viewHolder.itemView, over)
                } else {
                    super.onChildDraw(
                        c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive
                    )
                }
            }

            override fun onChildDrawOver(
                c: android.graphics.Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) return
                super.onChildDrawOver(
                    c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive
                )
            }
        })
        helper.attachToRecyclerView(binding.rvFavorites)
    }

    /**
     * 鬆手補位：找出螢幕中心落在哪一格 OTHER child；若 onMove 未改順序則現在 moveItem。
     * 必須在歸零 translation 前呼叫（中心座標才準）。
     */
    private fun reconcileDropIfNeeded(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        startPos: Int
    ) {
        val targetPos = findAdapterPositionUnderDragCenter(recyclerView, viewHolder)
        if (targetPos == RecyclerView.NO_POSITION) return

        if (adapter.orderEqualsSnapshot()) {
            if (startPos != RecyclerView.NO_POSITION && startPos != targetPos) {
                adapter.moveItem(startPos, targetPos)
            }
            return
        }

        // onMove 已部分換位：把目前這張再對準目標格
        val currentPos = viewHolder.bindingAdapterPosition
        if (currentPos != RecyclerView.NO_POSITION && currentPos != targetPos) {
            adapter.moveItem(currentPos, targetPos)
        }
    }

    /** 回傳中心點落在其 bounds 內的 OTHER child 的 adapter position；找不到則 NO_POSITION */
    private fun findAdapterPositionUnderDragCenter(
        recyclerView: RecyclerView,
        dragged: RecyclerView.ViewHolder
    ): Int {
        val itemView = dragged.itemView
        val itemLoc = IntArray(2)
        itemView.getLocationOnScreen(itemLoc)
        val cx = itemLoc[0] + itemView.width / 2
        val cy = itemLoc[1] + itemView.height / 2

        val childLoc = IntArray(2)
        for (i in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(i) ?: continue
            if (child === itemView) continue
            val holder = recyclerView.getChildViewHolder(child)
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) continue
            child.getLocationOnScreen(childLoc)
            val left = childLoc[0]
            val top = childLoc[1]
            val right = left + child.width
            val bottom = top + child.height
            if (cx in left..right && cy in top..bottom) {
                return pos
            }
        }
        return RecyclerView.NO_POSITION
    }

    /** 僅抬高被拖卡片；刪除區在 XML 已在 RV 之下，不必抬 RV／bringToFront */
    private fun applyDragCardElevation(itemView: View?) {
        if (itemView == null) return
        val z = DRAG_CARD_ELEVATION_DP * resources.displayMetrics.density
        itemView.elevation = z
        itemView.translationZ = z
    }

    private fun resetDragCardElevation(itemView: View?) {
        itemView?.elevation = 0f
        itemView?.translationZ = 0f
    }

    /** A: 只動 alpha／translationY，永不 GONE／INVISIBLE */
    private fun showDeleteZone() {
        zoneAnimator?.cancel()
        deleteZoneRevealed = true
        hideFabForDragWithoutGone()

        val zone = binding.deleteZone
        zone.animate().cancel()
        zone.visibility = View.VISIBLE
        zone.isClickable = false
        zone.isFocusable = false
        zone.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        zone.alpha = 0f
        zone.translationY = deleteZoneSlidePx
        zone.scaleX = 1f
        zone.scaleY = 1f
        zone.setBackgroundResource(R.drawable.bg_delete_zone)
        zoneAnimator = zone.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(DELETE_ZONE_FADE_MS)
            .setListener(null)
        zoneAnimator?.start()
    }

    private fun hideDeleteZone() {
        zoneAnimator?.cancel()
        val zone = binding.deleteZone
        zoneAnimator = zone.animate()
            .alpha(0f)
            .translationY(deleteZoneSlidePx)
            .setDuration(DELETE_ZONE_FADE_MS)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    zone.alpha = 0f
                    zone.translationY = 0f
                    zone.scaleX = 1f
                    zone.scaleY = 1f
                    zone.visibility = View.VISIBLE
                    zone.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    zone.setBackgroundResource(R.drawable.bg_delete_zone)
                    deleteZoneRevealed = false
                    restoreFabAfterDrag()
                }

                override fun onAnimationCancel(animation: Animator) {
                    zone.alpha = 0f
                    zone.visibility = View.VISIBLE
                    zone.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    deleteZoneRevealed = false
                    restoreFabAfterDrag()
                }
            })
        zoneAnimator?.start()
    }

    /** A: 拖曳中隱藏 FAB 只用 alpha，勿 View.GONE（避免 ConstraintLayout 重排） */
    private fun hideFabForDragWithoutGone() {
        val fab = binding.fabAdd
        fabWasInteractiveBeforeDrag =
            fab.visibility == View.VISIBLE && fab.alpha > 0.5f && fab.isEnabled
        if (fab.visibility != View.VISIBLE) {
            // 已因滿額等設成 GONE：保持不動
            return
        }
        fab.animate().cancel()
        fab.alpha = 0f
        fab.isEnabled = false
        fab.isClickable = false
    }

    private fun restoreFabAfterDrag() {
        val count = adapter.itemCount
        if (count >= AppConstants.MAX_FAVORITES) {
            binding.fabAdd.visibility = View.GONE
            binding.fabAdd.alpha = 1f
            binding.fabAdd.isEnabled = true
            binding.fabAdd.isClickable = true
            return
        }
        val fab = binding.fabAdd
        fab.visibility = View.VISIBLE
        if (fabWasInteractiveBeforeDrag) {
            fab.alpha = 1f
            fab.isEnabled = true
            fab.isClickable = true
        } else {
            fab.alpha = 1f
            fab.isEnabled = true
            fab.isClickable = true
        }
    }

    private fun applyFabAvailability(count: Int) {
        val fab = binding.fabAdd
        if (count >= AppConstants.MAX_FAVORITES) {
            fab.visibility = View.GONE
        } else {
            fab.visibility = View.VISIBLE
            fab.alpha = 1f
            fab.isEnabled = true
            fab.isClickable = true
        }
    }

    /** 卡疊上刪除區時：卡片半透明＋區半透明（並保留加深底／微放大） */
    private fun applyDeleteOverlapStyle(itemView: View, over: Boolean) {
        itemView.alpha = if (over) DRAG_CARD_DELETE_OVERLAP_ALPHA else 1f
        setDeleteZoneHovered(over)
    }

    private fun setDeleteZoneHovered(hovered: Boolean) {
        if (zoneHovered == hovered) return
        zoneHovered = hovered
        if (!deleteZoneRevealed) return
        val zone = binding.deleteZone
        zone.setBackgroundResource(
            if (hovered) R.drawable.bg_delete_zone_active else R.drawable.bg_delete_zone
        )
        val scale = if (hovered) 1.05f else 1f
        val alpha = if (hovered) DELETE_ZONE_OVERLAP_ALPHA else 1f
        zone.animate()
            .scaleX(scale)
            .scaleY(scale)
            .alpha(alpha)
            .setDuration(120L)
            .start()
    }

    /**
     * 以螢幕座標判定卡片是否疊在刪除區上（不依賴觸控是否送到 zone；zone 可在 RV 下面）。
     * 擴大 hit 區（四周 padding），並以「卡片矩形與區相交」為準——視覺重疊即算，不要求中心點落在窄條內。
     */
    private fun isViewOverDeleteZone(itemView: View): Boolean {
        if (!deleteZoneRevealed) return false
        val zone = binding.deleteZone
        if (zone.width <= 0 || zone.height <= 0) return false
        val pad = (DELETE_ZONE_HIT_PAD_DP * resources.displayMetrics.density).toInt()
        val zoneLoc = IntArray(2)
        val itemLoc = IntArray(2)
        zone.getLocationOnScreen(zoneLoc)
        itemView.getLocationOnScreen(itemLoc)
        val zoneRect = Rect(
            zoneLoc[0] - pad,
            zoneLoc[1] - pad,
            zoneLoc[0] + zone.width + pad,
            zoneLoc[1] + zone.height + pad
        )
        val itemRect = Rect(
            itemLoc[0],
            itemLoc[1],
            itemLoc[0] + itemView.width,
            itemLoc[1] + itemView.height
        )
        if (Rect.intersects(zoneRect, itemRect)) return true
        // 備援：中心或下半中心落入擴大區
        val cx = itemLoc[0] + itemView.width / 2
        val cy = itemLoc[1] + itemView.height / 2
        val cyLower = itemLoc[1] + (itemView.height * 3) / 4
        return zoneRect.contains(cx, cy) || zoneRect.contains(cx, cyLower)
    }

    private fun confirmDeleteFavorite(trip: FavoriteTrip) {
        val title = getString(
            R.string.confirm_delete_favorite,
            "${trip.routeLabel} 往${trip.destLabel}"
        )
        MaterialAlertDialogBuilder(this)
            .setMessage(title)
            .setNegativeButton(getString(R.string.cancel), null)
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                lifecycleScope.launch {
                    AppDatabase.get(this@MainActivity).favoriteDao().delete(trip)
                }
            }
            .show()
            .also { dialog ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    ?.setTextColor(ContextCompat.getColor(this, R.color.error_text))
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
        getSharedPreferences(AppConstants.PREFS_SETTINGS, MODE_PRIVATE)
    }

    private fun applySavedTheme() {
        val mode = prefs.getInt(AppConstants.KEY_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
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
        val current = prefs.getInt(AppConstants.KEY_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        val checked = modes.indexOf(current).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(R.string.appearance)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                val mode = modes[which]
                prefs.edit().putInt(AppConstants.KEY_NIGHT_MODE, mode).apply()
                AppCompatDelegate.setDefaultNightMode(mode)
                dialog.dismiss()
                recreate()
            }
            .show()
    }

    companion object {
        const val EXTRA_OPEN_ETA = "open_eta"
        private const val DELETE_ZONE_FADE_MS = 220L
        private const val DRAG_CARD_ELEVATION_DP = 12f
        /** 刪除區 hit 擴大（dp），避免窄條＋卡片中心偏上導致「看起來疊上卻不算」 */
        private const val DELETE_ZONE_HIT_PAD_DP = 28f
        /** 卡疊上刪除區時的半透明（提示將刪除） */
        private const val DRAG_CARD_DELETE_OVERLAP_ALPHA = 0.52f
        private const val DELETE_ZONE_OVERLAP_ALPHA = 0.7f
    }
}
