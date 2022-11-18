package com.example.moveup

import android.R
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.activityViewModels
import com.example.moveup.databinding.FragmentBluetoothBinding


class BluetoothFragment : Fragment() {

    private var _binding: FragmentBluetoothBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BasicViewModel by activityViewModels()
    private lateinit var adapter : ArrayAdapter<String>


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentBluetoothBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonScan.setOnClickListener {

        }

        // Adapter für den ListView
        adapter = ArrayAdapter(requireContext(),
            R.layout.simple_list_item_1,   // Layout zur Darstellung der ListItems
            viewModel.getTerminList()!!)           // Liste, die Dargestellt werden soll

        // Adapter an den ListView koppeln
        binding.listView.adapter = adapter

        // Mittels Observer den Adapter über Änderungen in der Liste informieren
        viewModel.terminList.observe(viewLifecycleOwner) { adapter.notifyDataSetChanged() }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}