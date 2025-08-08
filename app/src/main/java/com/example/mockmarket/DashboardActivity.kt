package com.example.mockmarket

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.mockmarket.databinding.ActivityDashboardBinding

class DashboardActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDashboardBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnGoToMarket.setOnClickListener {
            startActivity(Intent(this, MarketActivity::class.java))
        }

        binding.btnGoToPortfolio.setOnClickListener {
            startActivity(Intent(this, PortfolioActivity::class.java))
        }
    }
}
