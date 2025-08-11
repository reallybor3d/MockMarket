package com.example.mockmarket.app_ui

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.mockmarket.R
import com.example.mockmarket.databinding.ActivityMainBinding
import com.example.mockmarket.app_ui.LeaderboardFragment
import com.example.mockmarket.app_ui.StocksFragment
import com.example.mockmarket.app_ui.SettingsFragment

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) openFragment(LeaderboardFragment())

        binding.bottomNav.setOnItemSelectedListener { item: MenuItem ->
            when (item.itemId) {
                R.id.nav_leaderboard -> openFragment(LeaderboardFragment())
                R.id.nav_stocks      -> openFragment(StocksFragment())
                R.id.nav_settings    -> openFragment(SettingsFragment())
                else -> false
            }
            true
        }
    }
    private fun openFragment(f: Fragment): Boolean {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, f)
            .commit()
        return true
    }
    override fun onResume() {
        super.onResume()
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("users").document(uid).get()
            .addOnSuccessListener { snap ->
                val uname = snap.getString("username") ?: "(user)"
                val cash = snap.getDouble("cash") ?: 0.0
                val equity = snap.getDouble("equity") ?: (cash) // fallback
                binding.tvUsername.text = uname
                binding.tvMoney.text = String.format("$%,.2f (Equity: $%,.2f)", cash, equity)
            }
    }
}
