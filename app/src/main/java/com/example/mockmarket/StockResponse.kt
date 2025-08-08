package com.example.mockmarket

data class StockResponse(
    val c: Double, // Current price
    val h: Double, // High price of the day
    val l: Double, // Low price of the day
    val o: Double, // Open price of the day
    val pc: Double // Previous close price
)
