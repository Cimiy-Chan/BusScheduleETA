package org.cimiy.busscheduleeta

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.cimiy.busscheduleeta.data.StopOption
import org.cimiy.busscheduleeta.databinding.ItemStopBinding

class StopAdapter(
    private val onClick: (StopOption) -> Unit
) : RecyclerView.Adapter<StopAdapter.VH>() {

    private var items: List<StopOption> = emptyList()

    fun submit(list: List<StopOption>) {
        items = list
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemStopBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }
    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    inner class VH(private val b: ItemStopBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(s: StopOption) {
            b.tvSeq.text = s.seq.toString()
            b.tvStopName.text = s.nameTc
            b.root.setOnClickListener { onClick(s) }
        }
    }
}