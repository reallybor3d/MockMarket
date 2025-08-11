package com.example.mockmarket.app_ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.mockmarket.R
import com.example.mockmarket.data.PortfolioRepository
import com.example.mockmarket.data.StockApiService
import com.example.mockmarket.data.TimeSeriesValue
import com.example.mockmarket.data.TwelveDataResponse
import com.example.mockmarket.databinding.FragmentStocksBinding
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class StocksFragment : Fragment() {

    private var _binding: FragmentStocksBinding? = null
    private val binding get() = _binding!!

    // TODO: move API key to safer place later
    private val apiKey = "d1df3020933a4fdca8b35966e9e0d538" //API key

    private data class TimeFrame(val interval: String, val outputSize: Int)
    private val timeFrames = mapOf(
        R.id.btn1D to TimeFrame("1day", 30),
        R.id.btn1W to TimeFrame("1day", 5),
        R.id.btn1M to TimeFrame("1day", 22),
        R.id.btn1Y to TimeFrame("1month", 12),
        R.id.btnMax to TimeFrame("1month", 1000)
    )

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.twelvedata.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    private val service by lazy { retrofit.create(StockApiService::class.java) }

    private var lastPrice: Double = 0.0
    private var currentSymbol: String = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStocksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        setupChart()

        // Default load
        fetchAndRender("AAPL", "1day", 30)

        binding.btnSearch.setOnClickListener {
            val symbol = binding.etSymbol.text.toString().trim().uppercase()
            if (symbol.isEmpty()) {
                toast("Please enter a ticker")
            } else {
                fetchAndRender(symbol, "1day", 30)
            }
        }

        timeFrames.forEach { (btnId, tf) ->
            binding.root.findViewById<View>(btnId).setOnClickListener {
                fetchAndRender(getSymbolInput(), tf.interval, tf.outputSize)
            }
        }

        binding.btnBuy.setOnClickListener { place("BUY") }
        binding.btnSell.setOnClickListener { place("SELL") }
    }

    private fun getSymbolInput(): String =
        binding.etSymbol.text.toString().trim().uppercase().ifEmpty { "AAPL" }

    private fun setupChart() {
        binding.lineChart.description = Description().apply { text = "" }
        binding.lineChart.axisRight.isEnabled = false
        binding.lineChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        binding.lineChart.setNoDataText(getString(R.string.no_data))
    }

    private fun setLoading(loading: Boolean) {
        binding.btnSearch.isEnabled = !loading
        binding.btnBuy.isEnabled = !loading
        binding.btnSell.isEnabled = !loading
        binding.etSymbol.isEnabled = !loading
    }

    private fun fetchAndRender(symbol: String, interval: String, outputSize: Int) {
        currentSymbol = symbol
        setLoading(true)
        service.getTimeSeries(symbol, interval, apiKey, outputSize)
            .enqueue(object : Callback<TwelveDataResponse> {
                override fun onResponse(call: Call<TwelveDataResponse>, response: Response<TwelveDataResponse>) {
                    setLoading(false)
                    val body = response.body()
                    if (!response.isSuccessful || body == null || body.status != "ok" || body.values.isEmpty()) {
                        binding.lineChart.clear()
                        binding.lineChart.setNoDataText("No data for $symbol")
                        binding.lineChart.invalidate()
                        lastPrice = 0.0
                        return
                    }
                    // set last price from most recent value
                    val latest = body.values.firstOrNull()
                    lastPrice = latest?.close?.toDoubleOrNull() ?: 0.0
                    renderChart(symbol, body.values)
                }
                override fun onFailure(call: Call<TwelveDataResponse>, t: Throwable) {
                    setLoading(false)
                    toast("Network error: ${t.message}")
                }
            })
    }

    private fun renderChart(symbol: String, values: List<TimeSeriesValue>) {
        val entries = ArrayList<Entry>()
        val labels = ArrayList<String>()
        val processed = when {
            values.isEmpty() -> emptyList()
            values.size == 1 -> listOf(values[0], values[0])
            else -> values.reversed()
        }
        for ((idx, v) in processed.withIndex()) {
            val close = v.close.toFloatOrNull() ?: continue
            entries.add(Entry(idx.toFloat(), close))
            labels.add(v.datetime)
        }
        if (entries.isEmpty()) {
            binding.lineChart.clear()
            binding.lineChart.setNoDataText("No valid data to display")
            binding.lineChart.invalidate()
            return
        }
        val set = LineDataSet(entries, symbol).apply {
            setDrawCircles(false)
            lineWidth = 2f
            setDrawValues(false)
        }
        binding.lineChart.data = LineData(set)
        binding.lineChart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        binding.lineChart.xAxis.labelRotationAngle = 0f
        binding.lineChart.xAxis.granularity = 1f
        binding.lineChart.xAxis.setLabelCount(6, false)
        binding.lineChart.invalidate()
    }

    private fun place(side: String) {
        val qty = binding.etQty.text.toString().toDoubleOrNull() ?: 0.0
        if (qty <= 0 || currentSymbol.isBlank() || lastPrice <= 0.0) {
            toast("Enter symbol, qty and load price first")
            return
        }
        // Place order
        com.example.mockmarket.data.FirestoreRepository.placeOrder(
            symbol = currentSymbol,
            side = side,
            qty = qty,
            price = lastPrice,
            onOk = {
                toast("Order filled")
                // Recompute equity using the current symbol's last price (quick refresh)
                PortfolioRepository.refreshEquityWithQuotes(
                    quotes = mapOf(currentSymbol to lastPrice),
                    onDone = { /* optionally update a header via activity, if you expose one */ },
                    onErr = { /* you can toast it if you want */ }
                )
            },
            onErr = { msg -> toast(msg) }
        )
    }

    private fun toast(msg: String) =
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

    override fun onDestroyView() {
        super.onDestroyView(); _binding = null
    }
}
