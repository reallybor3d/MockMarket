package com.example.mockmarket.app_ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mockmarket.databinding.FragmentLeaderboardBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class LeaderboardFragment : Fragment() {

    private var _binding: FragmentLeaderboardBinding? = null
    private val binding get() = _binding!!
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val adapter = SimpleLeaderboardAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLeaderboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        db.collection("users")
            .orderBy("equity", Query.Direction.DESCENDING)
            .limit(100)
            .get()
            .addOnSuccessListener { qs ->
                val rows = qs.documents.mapIndexed { index, d ->
                    val uname = d.getString("username") ?: d.id.take(6)
                    val equity = d.getDouble("equity") ?: 0.0
                    LeaderboardRow(rank = index + 1, username = uname, equity = equity)
                }
                adapter.submit(rows)
            }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
