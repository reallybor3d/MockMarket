package com.example.mockmarket.app_ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.mockmarket.R
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
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory

class StocksFragment : Fragment() {

    private var _binding: FragmentStocksBinding? = null
    private val binding get() = _binding!!

    private val apiKey = "priavte" // API KEY

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

        // default
        fetchAndRender("AAPL", "1day", 30)

        binding.btnSearch.setOnClickListener {
            val symbol = binding.etSymbol.text.toString().trim().uppercase()
            if (symbol.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter a ticker", Toast.LENGTH_SHORT).show()
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
        binding.lineChart.setNoDataText(getString(com.example.mockmarket.R.string.no_data))
    }

    private fun fetchAndRender(symbol: String, interval: String, outputSize: Int) {
        currentSymbol = symbol
        service.getTimeSeries(symbol, interval, apiKey, outputSize)
            .enqueue(object : Callback<TwelveDataResponse> {
                override fun onResponse(call: Call<TwelveDataResponse>, response: Response<TwelveDataResponse>) {
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
                    Toast.makeText(requireContext(), "Network error: ${t.message}", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(requireContext(), "Enter symbol, qty and load price first", Toast.LENGTH_SHORT).show()
            return
        }
        com.example.mockmarket.data.FirestoreRepository.placeOrder(
            symbol = currentSymbol,
            side = side,
            qty = qty,
            price = lastPrice,
            onOk = { Toast.makeText(requireContext(), "Order filled", Toast.LENGTH_SHORT).show() },
            onErr = { msg -> Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show() }
        )
        // TODO: after order, recompute equity and refresh header/leaderboard if needed
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
