package com.example.semar_v4.header

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.semar_v4.R

class OtomatisFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_otomatis, container, false)

        val switchSchedule = view.findViewById<Switch>(R.id.switchSchedule)

        switchSchedule.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                AlertDialog.Builder(requireContext())
                    .setTitle("Konfirmasi Jadwal")
                    .setMessage("Apakah yakin sudah mengatur jadwal?")
                    .setPositiveButton("Ya") { _, _ ->
                        Toast.makeText(requireContext(), "Jadwal diaktifkan", Toast.LENGTH_SHORT).show()
                        // TODO: Kirim data jadwal ke ESP
                    }
                    .setNegativeButton("Tidak") { _, _ ->
                        switchSchedule.isChecked = false
                    }
                    .show()
            } else {
                Toast.makeText(requireContext(), "Jadwal dimatikan", Toast.LENGTH_SHORT).show()
            }
        }

        return view
    }
}
