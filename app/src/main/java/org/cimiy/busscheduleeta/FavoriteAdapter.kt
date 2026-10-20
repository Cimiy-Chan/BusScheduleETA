package org.cimiy.busscheduleeta

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
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
            b.tvCompany.visibility = View.GONE

            val colors = intArrayOf(
                R.color.fav_bg_0, R.color.fav_bg_1, R.color.fav_bg_2,
                R.color.fav_bg_3, R.color.fav_bg_4, R.color.fav_bg_5,
                R.color.fav_bg_6, R.color.fav_bg_7, R.color.fav_bg_8
            )
            b.root.setCardBackgroundColor(
                ContextCompat.getColor(b.root.context, colors[bindingAdapterPosition % colors.size])
            )


            b.root.setOnClickListener { onClick(t) }
            b.root.setOnLongClickListener { onLongClick(t); true }
        }
    }
}