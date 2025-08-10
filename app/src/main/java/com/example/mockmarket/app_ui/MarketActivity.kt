package com.example.mockmarket.app_ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.mockmarket.R
import com.example.mockmarket.data.StockApiService
import com.example.mockmarket.data.TimeSeriesValue
import com.example.mockmarket.data.TwelveDataResponse
import com.example.mockmarket.databinding.ActivityMarketBinding
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory

data class TimeFrame(val interval: String, val outputSize: Int)

class MarketActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMarketBinding
    private val apiKey = "private"

    private val timeFrames = mapOf(
        R.id.btn1D to TimeFrame("1day", 2), //day
        R.id.btn1W to TimeFrame("1day", 5), //week
        R.id.btn1M to TimeFrame("1day", 22), //month
        R.id.btn1Y to TimeFrame("1month", 12), //year
        R.id.btnMax to TimeFrame("1month", 1000) //max
    )

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

        // AAPL 1D is default
        fetchAndRender("AAPL", "1day", 30)

        // search button
        binding.btnFetch.setOnClickListener {
            val symbol = binding.etSymbol.text.toString().trim().uppercase()
            if (symbol.isEmpty()) {
                Toast.makeText(this, "Please enter a ticker", Toast.LENGTH_SHORT).show()
            } else {
                fetchAndRender(symbol, "1day", 30)
            }
        }

        // timeframe buttons
        timeFrames.forEach { (buttonId, timeFrame) ->
            findViewById<android.view.View>(buttonId).setOnClickListener {
                fetchAndRender(getSymbolInput(), timeFrame.interval, timeFrame.outputSize)
            }
        }
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

        val processedValues = when {
            values.isEmpty() -> emptyList()
            values.size == 1 -> listOf(values[0], values[0]) // Duplicate single point to allow chart rendering
            else -> values.reversed() // Reverse for proper left-to-right time order
        }

        for ((index, value) in processedValues.withIndex()) {
            val close = value.close.toFloatOrNull() ?: continue
            entries.add(Entry(index.toFloat(), close))
            labels.add(value.datetime)
        }

        if (entries.isEmpty()) {
            binding.lineChart.clear()
            binding.lineChart.setNoDataText("No valid data to display")
            binding.lineChart.invalidate()
            return
        }

        val set = LineDataSet(entries, symbol)
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
