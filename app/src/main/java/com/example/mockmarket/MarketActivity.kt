package com.example.mockmarket

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.mockmarket.databinding.ActivityMarketBinding
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory

class MarketActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMarketBinding
    private val apiKey = "private"

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.twelvedata.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val service by lazy { retrofit.create(StockApiService::class.java) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMarketBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupChart()

        // Default: AAPL 1D
        fetchAndRender("AAPL", "1day", 30)

        // Search button
        binding.btnFetch.setOnClickListener {
            val symbol = binding.etSymbol.text.toString().trim().uppercase()
            if (symbol.isEmpty()) {
                Toast.makeText(this, "Please enter a ticker", Toast.LENGTH_SHORT).show()
            } else {
                fetchAndRender(symbol, "1day", 30)
            }
        }

        // Timeframe buttons
        binding.btn1D.setOnClickListener  { fetchAndRender(getSymbolInput(), "1day", 30) }
        binding.btn1W.setOnClickListener  { fetchAndRender(getSymbolInput(), "1week", 30) }
        binding.btn1M.setOnClickListener  { fetchAndRender(getSymbolInput(), "1month", 30) }
        binding.btn1Y.setOnClickListener  { fetchAndRender(getSymbolInput(), "1month", 365) } // Approximates 1 year
        binding.btnMax.setOnClickListener { fetchAndRender(getSymbolInput(), "1month", 1000) } // Longest
    }

    private fun getSymbolInput(): String {
        return binding.etSymbol.text.toString().trim().uppercase().ifEmpty { "AAPL" }
    }

    private fun setupChart() {
        binding.lineChart.description = Description().apply { text = "" }
        binding.lineChart.axisRight.isEnabled = false
        binding.lineChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        binding.lineChart.setNoDataText("No data")
    }

    private fun fetchAndRender(symbol: String, interval: String, outputSize: Int) {
        service.getTimeSeries(symbol, interval, apiKey, outputSize)
            .enqueue(object : Callback<TwelveDataResponse> {
                override fun onResponse(call: Call<TwelveDataResponse>, response: Response<TwelveDataResponse>) {
                    val body = response.body()
                    if (!response.isSuccessful || body == null || body.status != "ok" || body.values.isEmpty()) {
                        binding.lineChart.clear()
                        binding.lineChart.setNoDataText("No data for $symbol")
                        binding.lineChart.invalidate()
                        return
                    }

                    renderChart(symbol, body.values)
                }

                override fun onFailure(call: Call<TwelveDataResponse>, t: Throwable) {
                    Toast.makeText(this@MarketActivity, "Network error: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun renderChart(symbol: String, values: List<TimeSeriesValue>) {
        val entries = ArrayList<Entry>()
        val labels = ArrayList<String>()

        for ((index, value) in values.reversed().withIndex()) {
            val close = value.close.toFloatOrNull() ?: continue
            entries.add(Entry(index.toFloat(), close))
            labels.add(value.datetime)
        }

        val set = LineDataSet(entries, "$symbol")
        set.setDrawCircles(false)
        set.lineWidth = 2f
        set.setDrawValues(false)

        val data = LineData(set)
        binding.lineChart.data = data

        binding.lineChart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        binding.lineChart.xAxis.labelRotationAngle = 0f
        binding.lineChart.xAxis.granularity = 1f
        binding.lineChart.xAxis.setLabelCount(6, false)

        binding.lineChart.invalidate()
    }
}
