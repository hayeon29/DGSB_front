package com.example.teamproject6

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.example.teamproject6.databinding.DialogBinding

class CustomDialog(val finishApp:() -> Unit): DialogFragment() {
    private var _binding: DialogBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = DialogBinding.inflate(inflater, container, false)
        val view = binding.root
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        binding.dialogNo.setOnClickListener {
            dismiss()
        }
        binding.dialogYes.setOnClickListener {
            finishApp()
        }
        return view
    }
}