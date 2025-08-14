package com.example.mockmarket.app_ui

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.mockmarket.R
import com.example.mockmarket.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            openFragment(DashboardFragment(), addToBackStack = false)
            binding.bottomNav.selectedItemId = R.id.nav_dashboard
        }

        binding.bottomNav.setOnItemSelectedListener { item: MenuItem ->
            when (item.itemId) {
                R.id.nav_dashboard   -> openFragment(DashboardFragment(), addToBackStack = false)
                R.id.nav_leaderboard -> openFragment(LeaderboardFragment(), addToBackStack = false)
                R.id.nav_stocks      -> openFragment(StocksFragment(), addToBackStack = false)
                R.id.nav_settings    -> openFragment(SettingsFragment(), addToBackStack = false)
                else -> false
            }
        }
    }

    fun openFragment(f: Fragment, addToBackStack: Boolean = true): Boolean {
        val tx = supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.fragmentContainer, f)

        if (addToBackStack) tx.addToBackStack(null)

        tx.commit()
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
                val equity = snap.getDouble("equity") ?: cash // fallback
                binding.tvUsername.text = uname
                binding.tvMoney.text = String.format("$%,.2f (Equity: $%,.2f)", cash, equity)
            }
    }
}
