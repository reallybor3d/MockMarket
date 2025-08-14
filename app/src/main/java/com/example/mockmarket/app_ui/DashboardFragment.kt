package com.example.mockmarket.app_ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.mockmarket.databinding.FragmentDashboardBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private var headerListener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        binding.swipe.setOnRefreshListener { loadOnce() }

        binding.btnStocks.setOnClickListener {
            (requireActivity() as MainActivity).openFragment(StocksFragment())
        }
        binding.btnLeaderboard.setOnClickListener {
            (requireActivity() as MainActivity).openFragment(LeaderboardFragment())
        }
        binding.btnSettings.setOnClickListener {
            (requireActivity() as MainActivity).openFragment(SettingsFragment())
        }

        // Realtime UPDATES
        attachRealtimeHeader()
        loadOnce()
    }

    private fun attachRealtimeHeader() {
        val uid = auth.currentUser?.uid ?: return
        // Cleaning from previous load
        headerListener?.remove()
        headerListener = db.collection("users").document(uid)
            .addSnapshotListener { snap, err ->
                if (!isAdded) return@addSnapshotListener
                if (err != null) {
                    toast("Live update error: ${err.message}")
                    return@addSnapshotListener
                }
                if (snap == null || !snap.exists()) return@addSnapshotListener

                val username = snap.getString("username") ?: "(unknown)"
                val cash    = snap.getDouble("cash") ?: 0.0
                val equity  = snap.getDouble("equity") ?: 0.0

                binding.tvUsername.text = username
                binding.tvCash.text     = money(cash)
                binding.tvEquity.text   = money(equity)
            }
    }

    private fun loadOnce() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            toast("Not signed in")
            binding.swipe.isRefreshing = false
            return
        }
        setButtonsEnabled(false)
        showLoading(true)

        db.collection("users").document(uid).get()
            .addOnSuccessListener { snap ->
                val username = snap.getString("username") ?: "(unknown)"
                val cash    = snap.getDouble("cash") ?: 0.0
                val equity  = snap.getDouble("equity") ?: 0.0

                binding.tvUsername.text = username
                binding.tvCash.text     = money(cash)
                binding.tvEquity.text   = money(equity)
            }
            .addOnFailureListener {
                toast("Failed to load: ${it.message}")
            }
            .addOnCompleteListener {
                showLoading(false)
                binding.swipe.isRefreshing = false
                setButtonsEnabled(true)
            }
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        binding.btnStocks.isEnabled = enabled
        binding.btnLeaderboard.isEnabled = enabled
        binding.btnSettings.isEnabled = enabled
    }

    private fun showLoading(loading: Boolean) {
        binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun toast(msg: String) =
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

    override fun onDestroyView() {
        super.onDestroyView()
        headerListener?.remove()
        headerListener = null
        _binding = null
    }
}

/** DONT MOVE THIS */
private fun money(x: Double): String = "$" + "%,.2f".format(x)
