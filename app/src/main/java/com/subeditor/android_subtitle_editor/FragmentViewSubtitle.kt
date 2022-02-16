package com.subeditor.android_subtitle_editor

import android.annotation.SuppressLint

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.ads.AdRequest
import com.subeditor.android_subtitle_editor.databinding.FragmentViewSubtitleBinding

class ViewSubtitleAdapter(private val dataSet: ArrayList<String>) : RecyclerView.Adapter<ViewSubtitleAdapter.ViewHolder>() {
    @SuppressLint("NotifyDataSetChanged")
    fun restore(currentFile: ArrayList<String>) {
        dataSet.clear()
        dataSet.addAll(currentFile)
        notifyDataSetChanged()
    }
    class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val subtitleButton: TextView = view.findViewById(R.id.subtitle_string)
    }
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.text_row_line, viewGroup, false)
        return ViewHolder(view)
    }
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val text = HtmlCompat.fromHtml(dataSet[position],
            HtmlCompat.FROM_HTML_SEPARATOR_LINE_BREAK_DIV
        )
        holder.subtitleButton.text = text
    }
    override fun getItemCount(): Int {
        return dataSet.size
    }
}

class FragmentViewSubtitle : Fragment() {
    private var _binding: FragmentViewSubtitleBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewAdapter : ViewSubtitleAdapter
    lateinit var toolbar: ToolBarHelper
    private val currentSub: CurrentSub by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentViewSubtitleBinding.inflate(inflater, container, false)
        val view = binding.root
        val resultFile = currentSub.subtitle.getCurrentResultingFile()
        viewAdapter = ViewSubtitleAdapter(resultFile)
        binding.viewRecycler.adapter = viewAdapter
        binding.viewRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.jumpToTheEnd.setOnClickListener {
            binding.viewRecycler.scrollToPosition(resultFile.size - 1)
        }
        binding.jumpToTheStart.setOnClickListener {
            binding.viewRecycler.scrollToPosition(0)
        }
        currentSub.subtitle.subtitleChange.observe(viewLifecycleOwner) {
            viewAdapter.restore(currentSub.subtitle.getCurrentResultingFile())
            currentSub.backupSession(this, it)
        }
        val adRequest = AdRequest.Builder().build()
        binding.adViewView.loadAd(adRequest)

        toolbar = ToolBarHelper()
        toolbar.getToolbar(this)
        toolbar.hideItems(listOf(R.id.view_toolbar, R.id.help_toolbar))
        return view
    }
    override fun onResume() {
        toolbar.showToolbar()
        super.onResume()
    }
    override fun onDetach() {
        if (this::toolbar.isInitialized) {
            toolbar.hideToolbar()
        }
        super.onDetach()
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}