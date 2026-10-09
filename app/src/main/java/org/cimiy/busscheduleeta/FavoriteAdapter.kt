package org.cimiy.busscheduleeta

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import org.cimiy.busscheduleeta.data.BusCompany
import org.cimiy.busscheduleeta.data.FavoriteTrip
import org.cimiy.busscheduleeta.databinding.ItemFavoriteBinding

class FavoriteAdapter(
    private val onClick: (FavoriteTrip) -> Unit,
    private val onLongClick: (FavoriteTrip) -> Unit
) : RecyclerView.Adapter<FavoriteAdapter.VH>() {

    private var items: List<FavoriteTrip> = emptyList()

    fun submit(list: List<FavoriteTrip>) {
        items = list
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

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
            val isKmb = t.company == BusCompany.KMB.name
            b.tvCompany.text = if (isKmb) "九巴" else "城巴"
            b.tvCompany.setTextColor(
                ContextCompat.getColor(
                    b.root.context,
                    if (isKmb) R.color.kmb_accent else R.color.ctb_accent
                )
            )
            b.root.setOnClickListener { onClick(t) }
            b.root.setOnLongClickListener { onLongClick(t); true }
        }
    }
}