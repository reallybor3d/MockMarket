package com.example.mockmarket.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

object QuotesRepository {

    // API KEY KEEP SECRET
    private const val API_KEY = "d1df3020933a4fdca8b35966e9e0d538"

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.twelvedata.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val service: StockApiService by lazy { retrofit.create(StockApiService::class.java) }

    fun fetchSeries(
        symbol: String,
        interval: String,
        outputSize: Int,
        onOk: (List<TimeSeriesValue>) -> Unit,
        onErr: (String) -> Unit
    ) {
        service.getTimeSeries(symbol, interval, API_KEY, outputSize)
            .enqueue(object : Callback<TwelveDataResponse> {
                override fun onResponse(call: Call<TwelveDataResponse>, resp: Response<TwelveDataResponse>) {
                    val body = resp.body()
                    if (!resp.isSuccessful || body == null || body.status != "ok") {
                        onErr("No data for $symbol"); return
                    }
                    onOk(body.values)
                }
                override fun onFailure(call: Call<TwelveDataResponse>, t: Throwable) {
                    onErr(t.message ?: "Network error")
                }
            })
    }

    fun getLastPrice(values: List<TimeSeriesValue>): Double {
        val latest = values.firstOrNull() ?: return 0.0
        return latest.close.toDoubleOrNull() ?: 0.0
    }
}
