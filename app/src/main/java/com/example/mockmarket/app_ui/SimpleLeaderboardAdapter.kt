package com.example.mockmarket.app_ui

import android.view.View
import android.view.ViewGroup

data class LeaderboardRow(val rank:Int, val username:String, val equity:Double)

class SimpleLeaderboardAdapter : androidx.recyclerview.widget.RecyclerView.Adapter<VH>() {
    private val data = mutableListOf<LeaderboardRow>()
    fun submit(rows: List<LeaderboardRow>) { data.clear(); data.addAll(rows); notifyDataSetChanged() }
    override fun onCreateViewHolder(p: ViewGroup, vType: Int): VH {
        val tv = android.widget.TextView(p.context)
        tv.setPadding(24, 24, 24, 24)
        return VH(tv)
    }
    override fun getItemCount() = data.size
    override fun onBindViewHolder(h: VH, i: Int) {
        val r = data[i]
        (h.itemView as android.widget.TextView).text =
            "${r.rank}. ${r.username} — $${"%,.2f".format(r.equity)}"
    }
}

class VH(v: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(v)
