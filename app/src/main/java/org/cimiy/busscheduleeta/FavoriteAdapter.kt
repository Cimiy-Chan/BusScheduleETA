package org.cimiy.busscheduleeta

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import org.cimiy.busscheduleeta.data.FavoriteTrip
import org.cimiy.busscheduleeta.databinding.ItemFavoriteBinding

/**
 * 首頁常用站卡片。長按拖曳由 ItemTouchHelper 接手。
 *
 * 拖曳期間略過 [submit]。成功換位後用 [pendingOrderIds] 擋住尚未寫入 DB 的舊 Flow。
 * [orderEqualsSnapshot] 供 clearView 在 onMove 未觸發時做鬆手補位。
 */
class FavoriteAdapter(
    private val onClick: (FavoriteTrip) -> Unit
) : RecyclerView.Adapter<FavoriteAdapter.VH>() {

    private var items: MutableList<FavoriteTrip> = mutableListOf()
    private var dragSnapshot: List<FavoriteTrip>? = null
    private var dragging: Boolean = false
    private var pendingOrderIds: List<Long>? = null

    fun submit(list: List<FavoriteTrip>) {
        if (dragging) return
        val pending = pendingOrderIds
        if (pending != null) {
            val incoming = list.map { it.id }
            if (incoming != pending) return
            pendingOrderIds = null
        }
        items = list.toMutableList()
        notifyDataSetChanged()
    }

    fun isDragging(): Boolean = dragging

    fun currentItems(): List<FavoriteTrip> = items.toList()

    fun itemAt(position: Int): FavoriteTrip? = items.getOrNull(position)

    /** 目前列表 id 順序是否仍等於拖曳開始時的 snapshot（表示 onMove 尚未換位） */
    fun orderEqualsSnapshot(): Boolean {
        val snap = dragSnapshot ?: return false
        if (snap.size != items.size) return false
        return items.indices.all { items[it].id == snap[it].id }
    }

    fun beginDrag() {
        dragging = true
        dragSnapshot = items.toList()
        pendingOrderIds = null
    }

    fun moveItem(from: Int, to: Int): Boolean {
        if (from == to) return false
        if (from !in items.indices || to !in items.indices) return false
        val item = items.removeAt(from)
        items.add(to, item)
        notifyItemMoved(from, to)
        return true
    }

    fun restoreSnapshot() {
        val snap = dragSnapshot ?: return
        items = snap.toMutableList()
        pendingOrderIds = null
        notifyDataSetChanged()
    }

    fun finishDrag(droppedOnDelete: Boolean): List<FavoriteTrip>? {
        if (droppedOnDelete) {
            restoreSnapshot()
            dragging = false
            dragSnapshot = null
            return null
        }
        val ordered = items.toList()
        pendingOrderIds = ordered.map { it.id }
        dragging = false
        dragSnapshot = null
        return ordered
    }

    override fun getItemCount() = items.size

    override fun getItemId(position: Int): Long =
        items.getOrNull(position)?.id ?: RecyclerView.NO_ID

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemFavoriteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    inner class VH(private val b: ItemFavoriteBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(t: FavoriteTrip) {
            b.tvRoute.text = t.routeLabel
            b.tvDest.text = "→ ${t.destLabel}"
            b.tvStop.text = t.stopLabel
            b.tvCompany.visibility = View.GONE

            val colors = intArrayOf(
                R.color.fav_bg_0, R.color.fav_bg_1, R.color.fav_bg_2,
                R.color.fav_bg_3, R.color.fav_bg_4, R.color.fav_bg_5,
                R.color.fav_bg_6, R.color.fav_bg_7, R.color.fav_bg_8
            )
            val colorIdx = ((t.id % colors.size) + colors.size) % colors.size
            b.root.setCardBackgroundColor(
                ContextCompat.getColor(b.root.context, colors[colorIdx.toInt()])
            )

            b.root.setOnClickListener {
                val p = bindingAdapterPosition
                if (p != RecyclerView.NO_POSITION) onClick(items[p])
            }
        }
    }

    init {
        setHasStableIds(true)
    }
}
