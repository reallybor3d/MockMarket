package com.example.mockmarket.app_ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mockmarket.data.StockApiService
import com.example.mockmarket.data.TwelveDataResponse
import com.example.mockmarket.databinding.FragmentPortfolioBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class PortfolioFragment : Fragment() {

    private var _binding: FragmentPortfolioBinding? = null
    private val binding get() = _binding!!

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private val apiKey = "d1df3020933a4fdca8b35966e9e0d538"

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.twelvedata.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    private val service by lazy { retrofit.create(StockApiService::class.java) }

    private val adapter by lazy { HoldingsAdapter() } // <- typed to PricedHolding in your project

    private var cash = 0.0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPortfolioBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter

        binding.swipe.setOnRefreshListener { loadEverything() }

        loadEverything()
    }

    private fun loadEverything() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            toast("Not signed in")
            binding.swipe.isRefreshing = false
            return
        }
        showLoading(true)

        val userRef = db.collection("users").document(uid)
        val holdingsRef = userRef.collection("holdings")

        // 1) read user (cash)
        userRef.get().addOnSuccessListener { snap ->
            cash = snap.getDouble("cash") ?: 0.0

            // 2) read holdings
            holdingsRef.get().addOnSuccessListener { qs ->
                val raw = qs.documents.mapNotNull { d ->
                    val sym = d.id
                    val qty = d.getDouble("qty") ?: 0.0
                    val avg = d.getDouble("avgCost") ?: 0.0
                    if (qty > 0) Holding(sym, qty, avg) else null
                }

                if (raw.isEmpty()) {
                    adapter.submitList(emptyList())
                    renderTotals(cash, 0.0, 0.0)

                    // keep backend equity/score consistent when no positions
                    com.example.mockmarket.data.PortfolioRepository.refreshEquityWithQuotes(
                        quotes = emptyMap(),
                        onDone = { /* no-op */ },
                        onErr = { /* no-op */ }
                    )

                    done()
                    return@addOnSuccessListener
                }

                // 3) fetch latest prices for each symbol
                fetchLatestPrices(raw) { priced ->
                    // Directly submit PricedHolding list to adapter
                    adapter.submitList(priced)

                    val equity = priced.sumOf { it.marketValue }
                    val pnl = priced.sumOf { it.unrealizedPnL }
                    renderTotals(cash, equity, pnl)

                    // Write fresh equity (positions only) + score(cash+equity) to Firestore
                    val quotes = priced.associate { it.symbol to it.lastPrice }
                    com.example.mockmarket.data.PortfolioRepository.refreshEquityWithQuotes(
                        quotes = quotes,
                        onDone = { /* optional */ },
                        onErr = { /* optional */ }
                    )

                    done()
                }
            }.addOnFailureListener {
                toast("Failed to load holdings: ${it.message}")
                done()
            }
        }.addOnFailureListener {
            toast("Failed to load user: ${it.message}")
            done()
        }
    }

    private fun fetchLatestPrices(
        holdings: List<Holding>,
        onComplete: (List<PricedHolding>) -> Unit
    ) {
        val out = mutableListOf<PricedHolding>()
        var remaining = holdings.size
        holdings.forEach { h ->
            service.getTimeSeries(
                symbol = h.symbol,
                interval = "1day",
                apiKey = apiKey,
                outputSize = 1
            ).enqueue(object : Callback<TwelveDataResponse> {
                override fun onResponse(
                    call: Call<TwelveDataResponse>,
                    response: Response<TwelveDataResponse>
                ) {
                    val price = response.body()
                        ?.takeIf { it.status == "ok" }
                        ?.values
                        ?.firstOrNull()
                        ?.close
                        ?.toDoubleOrNull() ?: 0.0
                    out += h.withPrice(price)
                    if (--remaining == 0) onComplete(out)
                }

                override fun onFailure(call: Call<TwelveDataResponse>, t: Throwable) {
                    out += h.withPrice(0.0)
                    if (--remaining == 0) onComplete(out)
                }
            })
        }
    }

    private fun renderTotals(cash: Double, equity: Double, pnl: Double) {
        binding.tvCash.text = money(cash)
        binding.tvEquity.text = money(equity)
        val sign = if (pnl >= 0) "+" else ""
        binding.tvPnL.text = "$sign${money(pnl)}"
    }

    private fun done() {
        showLoading(false)
        binding.swipe.isRefreshing = false
    }

    private fun showLoading(loading: Boolean) {
        binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun toast(msg: String) =
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

/** Local models **/
data class Holding(val symbol: String, val qty: Double, val avgCost: Double) {
    fun withPrice(price: Double): PricedHolding {
        val mv = price * qty
        val cost = avgCost * qty
        val pnl = mv - cost
        val changePct = if (cost > 0) (pnl / cost) * 100.0 else 0.0
        return PricedHolding(symbol, qty, avgCost, price, mv, pnl, changePct)
    }
}

data class PricedHolding(
    val symbol: String,
    val qty: Double,
    val avgCost: Double,
    val lastPrice: Double,
    val marketValue: Double,
    val unrealizedPnL: Double,
    val changePct: Double
)

private fun money(x: Double): String = "$" + "%,.2f".format(x)
