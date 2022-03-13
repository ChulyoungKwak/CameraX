package com.e.camerax

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.e.camerax.databinding.FragmentListBinding

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [ListFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class CustomDialog : DialogFragment() {
    var listener: OnCameraEventListener? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val binding = FragmentListBinding.inflate(inflater, container, false)
        binding.radioGroupBright.setOnCheckedChangeListener { _, checkedId ->
            listener?.onBrightChange(checkedId)
        }
        binding.radioGroupResolution.setOnCheckedChangeListener { _, checkedId ->
            listener?.onResolutionChange(checkedId)
        }
        binding.radioGroupZoom.setOnCheckedChangeListener { _, checkedId ->
            listener?.onZoomChange(checkedId)
        }
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        val width = (resources.displayMetrics.widthPixels * 0.8).toInt()
        val height = (resources.displayMetrics.heightPixels * 0.50).toInt()
        Log.d("Dialog", "Make layout")
        dialog!!.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    interface OnCameraEventListener {
        fun onBrightChange(checkedId: Int)
        fun onZoomChange(checkedId: Int)
        fun onResolutionChange(checkedId: Int)
    }
}