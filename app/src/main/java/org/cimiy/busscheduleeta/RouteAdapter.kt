package org.cimiy.busscheduleeta

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import org.cimiy.busscheduleeta.data.RouteOption
import org.cimiy.busscheduleeta.databinding.ItemRouteBinding

class RouteAdapter(
    private val onClick: (RouteOption) -> Unit
) : RecyclerView.Adapter<RouteAdapter.VH>() {

    private var all: List<RouteOption> = emptyList()
    private var shown: List<RouteOption> = emptyList()

    fun submit(list: List<RouteOption>) {
        all = list
        shown = list
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        val exactOnly = query.endsWith(' ') || query.endsWith('　') // 半形或全形空格
        val q = query.trim()
        shown = when {
            q.isEmpty() -> all
            exactOnly -> all.filter { it.route.equals(q, ignoreCase = true) }
            else -> all.filter { it.route.startsWith(q, ignoreCase = true) }
                .sortedWith(
                    compareBy<RouteOption> { !it.route.equals(q, ignoreCase = true) } // 完全相同最前
                        .thenBy { it.route.length }
                        .thenBy { it.route }
                        .thenBy { it.company }
                )
        }
        notifyDataSetChanged()
    }

    override fun getItemCount() = shown.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemRouteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }
    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(shown[position])

    inner class VH(private val b: ItemRouteBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(r: RouteOption) {
            val isKmb = r.company == "KMB"
            b.tvCompany.text = if (isKmb) "九巴" else "城巴"
            b.tvCompany.setTextColor(
                ContextCompat.getColor(b.root.context, if (isKmb) R.color.kmb_accent else R.color.ctb_accent)
            )
            b.tvRoute.text = r.route
            b.tvEndpoints.text = "${r.origTc} ↔ ${r.destTc}"
            b.root.setOnClickListener { onClick(r) }
        }
    }
}