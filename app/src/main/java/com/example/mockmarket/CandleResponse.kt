package com.example.mockmarket

data class CandleResponse(
    val c: List<Double>?, // close
    val h: List<Double>?, // high (unused for now)
    val l: List<Double>?, // low  (unused for now)
    val o: List<Double>?, // open (unused for now)
    val t: List<Long>?,   // timestamps (unix seconds)
    val s: String         // status: "ok" or "no_data"
)
