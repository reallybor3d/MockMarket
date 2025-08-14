package com.example.mockmarket.app_ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mockmarket.R
import com.example.mockmarket.databinding.ItemHoldingBinding
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

class HoldingsAdapter(
    private val onClick: (PricedHolding) -> Unit = {}
) : ListAdapter<PricedHolding, HoldingsAdapter.VH>(DIFF) {

    fun submit(items: List<PricedHolding>) = submitList(items)

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<PricedHolding>() {
            override fun areItemsTheSame(oldItem: PricedHolding, newItem: PricedHolding): Boolean =
                oldItem.symbol == newItem.symbol

            override fun areContentsTheSame(oldItem: PricedHolding, newItem: PricedHolding): Boolean =
                oldItem == newItem
        }

        private val currencyFmt: NumberFormat =
            NumberFormat.getCurrencyInstance(Locale.US)

        private fun currency(v: Double): String = currencyFmt.format(v)
        private fun Double.round1(): Double = round(this * 10.0) / 10.0
    }

    inner class VH(val b: ItemHoldingBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val inf = LayoutInflater.from(parent.context)
        return VH(ItemHoldingBinding.inflate(inf, parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = getItem(position)
        val b = holder.b
        val ctx = b.root.context

        b.tvSymbol.text = p.symbol
        b.tvQty.text    = ctx.getString(R.string.qty_fmt, p.qty)
        b.tvAvg.text    = ctx.getString(R.string.avg_fmt, currency(p.avgCost))
        b.tvPrice.text  = ctx.getString(R.string.price_fmt, currency(p.lastPrice))
        b.tvValue.text  = ctx.getString(R.string.value_fmt, currency(p.marketValue))

        val signValue = currency(abs(p.unrealizedPnL))
        b.tvPnL.text = ctx.getString(
            R.string.pnl_fmt,
            (if (p.unrealizedPnL >= 0) "+" else "–") + signValue,
            p.changePct.round1()
        )

        b.root.setOnClickListener { onClick(p) }
    }
}
