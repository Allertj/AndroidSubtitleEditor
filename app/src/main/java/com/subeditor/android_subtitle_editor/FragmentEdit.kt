package com.subeditor.android_subtitle_editor

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Adapter
import android.widget.Filter
import androidx.recyclerview.widget.ListAdapter
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.*
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.ads.AdRequest
import com.google.android.material.color.MaterialColors
import com.subeditor.android_subtitle_editor.databinding.FragmentEditBinding
import kotlinx.coroutines.runBlocking
import java.io.Serializable

object ReceivedInfo : Serializable {
    const val FIRST_SELF = 0
    const val LAST_SELF = 1
    const val FIRST_OTHER = 2
    const val LAST_OTHER = 3
    const val PLAY_DATA_FIRST = 22
    const val PLAY_DATA_LAST = 23
    lateinit var otherSubtitle : Subtitle
    var otherSubInitialized = false
    lateinit var currentSub: CurrentSub
    var values1 = hashMapOf<Int, SubtitleLine?>(FIRST_SELF to null,
                                                LAST_SELF to null,
                                                FIRST_OTHER to null,
                                                LAST_OTHER to null,
                                                PLAY_DATA_FIRST to null,
                                                PLAY_DATA_LAST to null)
    fun resetValues() {
        values1.keys.forEach {
            values1[it] = null
        }
    }
    fun loadOtherSubtitle(subtitle: Subtitle){
        otherSubtitle = subtitle
        otherSubInitialized = true
        values1[FIRST_OTHER] = otherSubtitle.getLine(0)
        values1[LAST_OTHER]  = otherSubtitle.getLine(otherSubtitle.size()-1)
    }
    fun loadValue(asked: Int, result: Int) {
        if (asked == FIRST_OTHER || asked == LAST_OTHER) {
            values1[asked] = otherSubtitle.getLine(result)
        } else {
            values1[asked] = currentSub.subtitle.getLine(result)
        }
    }
    private fun checkBound(asked: Int) : Int {
        return when {  asked % 2 == 0      -> {  0 }
            asked == LAST_OTHER -> { otherSubtitle.subtitleLines.lastIndex }
            else -> { currentSub.subtitle.size().minus(1) }
        }
    }
    fun getValue(asked: Int) : Int {
        val result = if (asked == FIRST_OTHER || asked == LAST_OTHER) {
            return if (otherSubInitialized) {
                otherSubtitle.findIndexOfIndividualSubtitle(values1[asked])
            } else {
                99999999
            }
        } else {
            currentSub.subtitle.findIndexOfIndividualSubtitle(values1[asked])
        }
        return if (result == -1) {
            checkBound(asked)
        } else {
            result
        }
    }
}
class SubtitleDiffItemCallback : DiffUtil.ItemCallback<SubtitleLine>() {
    override fun areItemsTheSame(oldItem: SubtitleLine, newItem: SubtitleLine): Boolean {
        return oldItem.startTime == newItem.startTime && oldItem.getJustTheText() == newItem.getJustTheText()
    }
    override fun areContentsTheSame(oldItem: SubtitleLine, newItem: SubtitleLine): Boolean {
        return oldItem.startTime == newItem.startTime && oldItem.getJustTheText() == newItem.getJustTheText()
    }
}
class SubtitleListAdapter(var usedSubtitle: Subtitle, private val asked: Int, val fragment: Fragment): ListAdapter<SubtitleLine, SubtitleListAdapter.SubtitleViewHolder>(SubtitleDiffItemCallback()) {
    fun callFromSwipe(position: Int) {
        fragment.requireActivity().runOnUiThread {
            runBlocking {
                usedSubtitle.removeAtBySwipe(position)
            }
        }
    }
    class SubtitleViewHolder(val view: View,
                             private val usedSubtitle: Subtitle,
                             private val asked: Int,
                             val fragment: Fragment
    ) : RecyclerView.ViewHolder(view) {
        val subtitleButton: TextView = view.findViewById(R.id.subtitle_button)
        private val currentSub: CurrentSub by fragment.activityViewModels()
        init {
            subtitleButton.setOnClickListener{
                if (asked == 290) {
                    val index2 = usedSubtitle.findSubtitleLineById(subtitleButton.tag as Int)
                    val action = FragmentEditDirections.actionFragmentEditToFragmentEditSingle(index2)
                    view.findNavController().navigate(action)
                } else {
                    val index1 = absoluteAdapterPosition
                    if (index1 != Adapter.NO_SELECTION) {
                        var index2 = usedSubtitle.findSubtitleLineById(subtitleButton.tag as Int)
                        if (asked == ReceivedInfo.FIRST_OTHER || asked == ReceivedInfo.LAST_OTHER) {
                            index2 = ReceivedInfo.otherSubtitle.findSubtitleLineById(subtitleButton.tag as Int)
                        }
                        ReceivedInfo.loadValue(asked, index2)
                        currentSub.backupReceivedInfo(fragment)
                    }
                    when (fragment.javaClass.simpleName) {
                        "FragmentNewPlayer" ->  (fragment as FragmentNewPlayer).updateText()
                        "FragmentSyncWithOtherSub" -> (fragment as FragmentSyncWithOtherSub).updateText()
                    }
                    fragment.parentFragmentManager.popBackStack()
                }
            }
        }
    }
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): SubtitleViewHolder {
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.text_row_item, viewGroup, false)
        return SubtitleViewHolder(view, usedSubtitle, asked, fragment)
    }

    override fun onBindViewHolder(viewHolder: SubtitleViewHolder, position: Int) {
        viewHolder.subtitleButton.text = getItem(position).toSpannable()
        viewHolder.subtitleButton.tag = getItem(position).id
    }
    fun getFilter(): Filter {
        return cityFilter
    }
    private val cityFilter = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val filteredList: ArrayList<SubtitleLine> = ArrayList()
            if (constraint == null || constraint.isEmpty()) {
                usedSubtitle.subtitleLines.let { filteredList.addAll(it) }
            } else {
                val query = constraint.toString().trim().lowercase()
                usedSubtitle.subtitleLines.forEach {
                    if (it.getJustTheText().lowercase().contains(query)) {
                        filteredList.add(it)
                    }
                }
            }
            val results = FilterResults()
            results.values = filteredList
            return results
        }
        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            if (results?.values is ArrayList<*>) {
                val res = results.values as ArrayList<*>
                val kk = arrayListOf<SubtitleLine>()
                res.forEach { kk.add(it as SubtitleLine) }
                this@SubtitleListAdapter.submitList(kk.toList())
            }
        }
    }
}

class FragmentEdit : Fragment() {
    private lateinit var currentSubtitle : Subtitle
    private var loadedSubtitle : Subtitle? = null
    private lateinit var adapter: SubtitleListAdapter
    private lateinit var toolbar: ToolBarHelper
    private var asked = 290
    private val currentSub: CurrentSub by activityViewModels()
    private lateinit var fragment1: Fragment
    fun receive(typeAsked: Int, subtitle: Subtitle, fragment: Fragment?=null) {
        asked = typeAsked
        loadedSubtitle = subtitle
        if (fragment != null) {
            fragment1 = fragment
        }
    }
    private var _binding: FragmentEditBinding? = null
    private val binding get() = _binding!!
    private var editing = false
    @SuppressLint("NotifyDataSetChanged")
    fun restore(change: SubtitleChange) {
        requireActivity().runOnUiThread {
            runBlocking {
                if (change.singleOrComplete == SubtitleChange.COMPLETE) {
                    adapter.notifyDataSetChanged()
                } else {
                    when (change.type) {
                        SubtitleChange.INSERTION -> adapter.notifyItemInserted(change.index)
                        SubtitleChange.DELETION -> adapter.notifyItemRemoved(change.index)
                        SubtitleChange.CHANGE -> adapter.notifyItemChanged(change.index)
                    }
                }
            }
        }
    }
    private fun createAdapter(view: View) {
        val wholeBody = binding.fragmentEditMain
        val color = MaterialColors.getColor(view, R.attr.colorOnBackground)
        wholeBody.setBackgroundColor(color)
        wholeBody.isClickable = true
        if (loadedSubtitle == null) {
            currentSubtitle = currentSub.subtitle
            editing = true
        } else {
            currentSubtitle = loadedSubtitle as Subtitle
        }
        if (asked == 290) {fragment1 = this}

        adapter = SubtitleListAdapter(currentSubtitle, asked, fragment1)
        adapter.submitList(currentSubtitle.subtitleLines)
        val subtitleView = binding.recyclerForSubtitles
        val addSwipe = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(v: RecyclerView, h: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false
            override fun onSwiped(h: RecyclerView.ViewHolder, dir: Int) {
                requireActivity().runOnUiThread {
                    adapter.callFromSwipe(h.absoluteAdapterPosition)
                }
            }
        })

        subtitleView.adapter = adapter
        subtitleView.layoutManager = LinearLayoutManager(requireContext())
        binding.scrollUp.setOnClickListener {
            subtitleView.scrollToPosition(0)
        }
        val searchBar = binding.searchBar
        searchBar.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                adapter.getFilter().filter(query)
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                adapter.getFilter().filter(newText)
                return true
            }

        })
        binding.scrollDown.setOnClickListener {
            val currentLength = subtitleView.adapter?.itemCount as Int - 1
            subtitleView.scrollToPosition(currentLength)
        }
        binding.searchOpener.setOnClickListener {
            if (searchBar.visibility == View.VISIBLE) {
                searchBar.visibility = View.GONE
            } else {
                searchBar.visibility = View.VISIBLE
            }
        }
        if (asked % 2 != 0) {
            subtitleView.scrollToPosition(currentSubtitle.size() - 1)
        }
        if (editing) {
            toolbar = ToolBarHelper()
            toolbar.getToolbar(this)
            toolbar.addDescription(getString(R.string.FragmentEdit))
        }

        if (currentSubtitle == currentSub.subtitle) {
            addSwipe.attachToRecyclerView(subtitleView)
            currentSubtitle.subtitleChange.observe(viewLifecycleOwner, Observer {
                currentSub.backupSession(this, it)
                if (it != null) {

                    restore(it) }
            })
        }
    }
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditBinding.inflate(inflater, container, false)
        val adRequest = AdRequest.Builder().build()
        binding.adViewEdit.loadAd(adRequest)
        return binding.root
    }
    override fun onResume() {
        createAdapter(binding.root)
        if (editing) {
            toolbar.showToolbar()
        }
        super.onResume()
    }
    override fun onDetach() {
        if (editing && this::toolbar.isInitialized) {
            toolbar.hideToolbar()
        }
        super.onDetach()
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}