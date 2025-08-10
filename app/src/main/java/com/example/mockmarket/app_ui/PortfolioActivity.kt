package com.example.mockmarket.app_ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.mockmarket.databinding.ActivityPortfolioBinding

class PortfolioActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPortfolioBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPortfolioBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Sample placeholder logic — update later with real data
        val samplePortfolioText = """
            Portfolio:
            • AAPL - 10 shares
            • TSLA - 3 shares
            • AMZN - 5 shares
        """.trimIndent()

        binding.tvPortfolio.text = samplePortfolioText
    }
}
