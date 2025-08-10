package com.example.mockmarket.data

data class TwelveDataResponse(
    val status: String,
    val values: List<TimeSeriesValue>
)

data class TimeSeriesValue(
    val datetime: String,
    val open: String,
    val high: String,
    val low: String,
    val close: String,
    val volume: String
)
