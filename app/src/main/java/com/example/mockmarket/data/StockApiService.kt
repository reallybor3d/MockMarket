package com.example.mockmarket.data

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface StockApiService {
    @GET("time_series")
    fun getTimeSeries(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("apikey") apiKey: String,
        @Query("outputsize") outputSize: Int
    ): Call<TwelveDataResponse>
}
