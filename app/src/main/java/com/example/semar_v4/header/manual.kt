package com.example.semar_v4.header

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.semar_v4.R

class ManualFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_manual, container, false)

        val btnRelayOn = view.findViewById<Button>(R.id.btnRelayOn)
        val btnRelayOff = view.findViewById<Button>(R.id.btnRelayOff)

        btnRelayOn.setOnClickListener {
            Toast.makeText(requireContext(), "Relay dinyalakan", Toast.LENGTH_SHORT).show()
            // TODO: Kirim perintah ke ESP
        }

        btnRelayOff.setOnClickListener {
            Toast.makeText(requireContext(), "Relay dimatikan", Toast.LENGTH_SHORT).show()
            // TODO: Kirim perintah ke ESP
        }

        return view
    }
}
