package com.subeditor.android_subtitle_editor

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.text.Html
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.res.ResourcesCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.*
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.flask.colorpicker.ColorPickerView
import com.flask.colorpicker.builder.ColorPickerDialogBuilder
import com.subeditor.android_subtitle_editor.databinding.FragmentAdvancedBinding
import java.nio.charset.Charset


class CharsetAdapter(private val dataSet: ArrayList<String>, private val fragment: FragmentAdvanced) : RecyclerView.Adapter<CharsetAdapter.ViewHolder>() {
//    private val currentSub: CurrentSub by fragment.activityViewModels()
    val copyOfCharsetList = ArrayList<String>().apply {
        addAll(dataSet)
    }
    fun implementCharsetChange(charset : Charset) {
        CharsetChange.chosenCharset = charset
        fragment.implementCharsetChange()
    }
    class ViewHolder(val view: View, private val dataSet: ArrayList<String>, private val adapter: CharsetAdapter, val fragment: Fragment) : RecyclerView.ViewHolder(view), View.OnClickListener{
        val subtitleButton: TextView = view.findViewById(R.id.subtitle_button)
        init {
            subtitleButton.setOnClickListener(this)
        }
        override fun onClick(v: View?) {
            val index1 = absoluteAdapterPosition
            val result = dataSet[index1]
            Charset.availableCharsets().forEach {
                if (it.key == result) {
                    adapter.implementCharsetChange(it.value)
                }
            }
        }
    }
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.text_row_item, viewGroup, false)
        return ViewHolder(view, dataSet, this, fragment)
    }
    override fun getItemCount() = dataSet.size
    fun getFilter(): Filter {
        return cityFilter
    }
    private val cityFilter = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val filteredList: ArrayList<String> = ArrayList()
            if (constraint == null || constraint.isEmpty()) {
                copyOfCharsetList.let { filteredList.addAll(it) }
            } else {
                val query = constraint.toString().trim().lowercase()
                copyOfCharsetList.forEach {
                    if (it.lowercase().contains(query)) {
                        filteredList.add(it)
                    }
                }
            }
            val results = FilterResults()
            results.values = filteredList
            return results
        }
        @SuppressLint("NotifyDataSetChanged")
        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            if (results?.values is ArrayList<*>) {
                dataSet.clear()
                val res = results.values as ArrayList<*>
                res.forEach { dataSet.add(it as String) }
                notifyDataSetChanged()
            }
        }
    }
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.subtitleButton.text = dataSet[position]
    }
}

class PickColorFragment : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return ColorPickerDialogBuilder
            .with(requireContext())
            .setTitle(getString(R.string.choose_color))
            .initialColor(Color.WHITE)
            .showAlphaSlider(false)
            .wheelType(ColorPickerView.WHEEL_TYPE.CIRCLE)
            .density(12)
            .setPositiveButton(
                getString(R.string.ok)
            ) { _, selectedColor, _ ->
                val chosenColor = Integer.toHexString(selectedColor).drop(2)
                setFragmentResult("requestColor", bundleOf("chosenColor" to "#$chosenColor")) }
            .setNegativeButton(getString(R.string.cancel)) { _, _ -> }
            .build()
    }
}

class AskConfirmationFragment: DialogFragment() {
    var result = false
    var message = ""
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        if (savedInstanceState != null) { message = savedInstanceState.getString("Message")!! }
        val confirm = android.app.AlertDialog.Builder(requireContext())
            .setMessage(message)
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                result = true
                setFragmentResult("requestConfirm", bundleOf("Confirmed" to result))
            }
            .setNegativeButton(getString(R.string.no)) { _, _ ->
                result = false
                setFragmentResult("requestConfirm", bundleOf("Confirmed" to result))
            }
        return confirm.show()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("Message", message)
        super.onSaveInstanceState(outState)
    }
}

class ApplyMarkupFragment : DialogFragment() {
    var color = "#FFC700"
    private lateinit var selectedColorTextView: TextView
    private lateinit var currentBundle : Bundle
    private fun getColorFromTheme() {
        val typedValue = TypedValue()
        requireContext().theme.resolveAttribute(R.attr.colorPrimary, typedValue, true)
        val firstColor =  "#" + Integer.toHexString(typedValue.data).substring(2)
        selectedColorTextView.backgroundTintList = ColorStateList.valueOf(Color.parseColor(firstColor))
    }
    private fun getColor() {
        PickColorFragment().show(childFragmentManager, "")
        childFragmentManager.setFragmentResultListener("requestColor", this)
        { _, bundle ->
            val resultReceived = bundle.getString("chosenColor")
            if (resultReceived != null) {
                color = resultReceived
                selectedColorTextView.backgroundTintList = ColorStateList.valueOf(Color.parseColor(color))
            }
        }
    }
    private fun getConfirmation() {
        val askConfirmation = AskConfirmationFragment()
        askConfirmation.message = getString(R.string.apply_markup)
        askConfirmation.show(childFragmentManager, "")
        childFragmentManager.setFragmentResultListener("requestConfirm", this)
        { _, bundle ->
            val resultReceived = bundle.getBoolean("Confirmed")
            if (resultReceived) {
                currentBundle.putBoolean("apply", true)
                setFragmentResult("chosenMarkup", currentBundle)
                this.dismiss()
            } else {
                currentBundle.putBoolean("apply", false)
                setFragmentResult("chosenMarkup", currentBundle)
                this.dismiss()
            }
        }
    }
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dd = Dialog(requireContext())
        dd.setContentView(R.layout.markup_dialog)

        val selectedColor = dd.findViewById<TextView>(R.id.selected_color)
        selectedColorTextView = selectedColor
        getColorFromTheme()
        selectedColor.setOnClickListener {
            getColor()
        }
        dd.findViewById<TextView>(R.id.ok_button).setOnClickListener {
            val italic = dd.findViewById<CheckBox>(R.id.italicsCheckBox).isChecked
            val boldness = dd.findViewById<CheckBox>(R.id.boldnessCheckBox).isChecked
            val underLine = dd.findViewById<CheckBox>(R.id.underlineCheckBox).isChecked
            val colorBoolean = dd.findViewById<CheckBox>(R.id.colorCheckBox).isChecked
            currentBundle = bundleOf("italic" to italic,
                                            "boldness" to boldness,
                                            "underline " to underLine,
                                            "applyColor" to colorBoolean,
                                            "color" to color)
            getConfirmation()
        }
        dd.findViewById<TextView>(R.id.cancel_button).setOnClickListener {
            dismiss()
        }
        return dd
    }
}

class QuestionFragment : DialogFragment() {
    private val currentSub: CurrentSub by activityViewModels()
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val confirm = android.app.AlertDialog.Builder(requireContext())
            .setMessage(getString(R.string.delete_styling))
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                currentSub.subtitle.deleteAllMarkUp()
                Toast.makeText(requireContext(), getString(R.string.all_markup_removed), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.no)) { _, _ ->
            }
        return confirm.show()
    }
}

object CharsetChange {
    var charsetAdapterVisible = View.GONE
    var resultViewVisible = View.GONE
    var resultSubtitle = ""
    lateinit var chosenCharset : Charset
    fun reset(charset: Charset) {
        charsetAdapterVisible = View.GONE
        resultViewVisible = View.GONE
        resultSubtitle = ""
        chosenCharset = charset
    }
}

class FragmentAdvanced : Fragment() {
    private var _binding: FragmentAdvancedBinding? = null
    val binding get() = _binding!!
    var color = "#FFC700"
    private lateinit var toolbar : ToolBarHelper
    private val currentSub: CurrentSub by activityViewModels()
    private fun reloadNewSubtitle(newSubtitleAsString: String, charset: Charset) {
        if (currentSub.subtitle.fileType == Subs.SRT) {
            val newSubtitle = SrtSubtitleCreator(newSubtitleAsString).build()
            currentSub.subtitle.actions.forEach {
                it.redoAction(newSubtitle)
            }
            newSubtitle.actions = currentSub.subtitle.actions
            newSubtitle.fileType = Subs.SRT
            newSubtitle.usedCharSet = charset.toString()
            newSubtitle.contentsOfFileAsByteArray = currentSub.subtitle.contentsOfFileAsByteArray
            currentSub.subtitle = newSubtitle
            findNavController().navigate(R.id.fragmentMainMenu)

        } else if (currentSub.subtitle.fileType == Subs.SUB) {
            val newSubtitle = SubSubtitleCreator(newSubtitleAsString, currentSub.subtitle.frameRate).build()
            currentSub.subtitle.actions.forEach {
                it.redoAction(newSubtitle)
            }
            newSubtitle.fileType = Subs.SUB
            newSubtitle.usedCharSet = charset.toString()
            newSubtitle.frameRate = currentSub.subtitle.frameRate
            newSubtitle.contentsOfFileAsByteArray = currentSub.subtitle.contentsOfFileAsByteArray
            currentSub.subtitle = newSubtitle
            findNavController().navigate(R.id.fragmentMainMenu)
        }
    }
    private fun placeText(newSubtitleAsString: String) {
        val segments = newSubtitleAsString.chunked(2000)
        binding.textResult.text = segments[0]
        binding.resultView.visibility = View.VISIBLE
        segments.drop(1).forEach{
            val dd = TextView(requireContext())
            dd.text = it
            val font = ResourcesCompat.getFont(requireContext(), R.font.proxima_nova_regular)
            dd.typeface = font
            binding.segmentHolder.addView(dd)
        }
    }
    fun implementCharsetChange() {
//        Toast.makeText(fragment.requireContext(), fragment.getString(R.string.conversion_in_progress), Toast.LENGTH_LONG).show()
        val newSubtitle = String(currentSub.subtitle.contentsOfFileAsByteArray as ByteArray, CharsetChange.chosenCharset)
        CharsetChange.resultSubtitle = newSubtitle
        binding.charsetRecyclerLayout.visibility = View.GONE
        CharsetChange.charsetAdapterVisible = binding.charsetRecyclerLayout.visibility
        placeText(newSubtitle)
        CharsetChange.resultViewVisible = binding.resultView.visibility
    }
    private fun initializeRecycler() {
        val charsetRecyclerLayout = binding.charsetRecyclerLayout
        val charsetRecycler = binding.charsetRecycler
        charsetRecyclerLayout.visibility = View.VISIBLE
        CharsetChange.charsetAdapterVisible = charsetRecyclerLayout.visibility
        val names = arrayListOf<String>()
        names.addAll(Charset.availableCharsets().keys)
        val charAdapter = CharsetAdapter(names, this)
        charsetRecycler.adapter = charAdapter
        charsetRecycler.layoutManager = LinearLayoutManager(requireContext())
        val searchBar = binding.charsetSearchView
        searchBar.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                charAdapter.getFilter().filter(query)
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                charAdapter.getFilter().filter(newText)
                return true
            }
        })
    }
    private fun confirmDeletion() {
        QuestionFragment().show(childFragmentManager, "")
    }
    private fun createMarkupForEntireSubtitle() {
        val createDialog = ApplyMarkupFragment()
        createDialog.show(childFragmentManager, "")
        childFragmentManager.setFragmentResultListener("chosenMarkup", this)
        { _, bundle ->
            val italic = bundle.getBoolean("italic")
            val boldness = bundle.getBoolean("boldness")
            val underLine = bundle.getBoolean("underline")
            val applyColor = bundle.getBoolean("applyColor")
            val apply = bundle.getBoolean("apply")
            val color = bundle.getString("color")
            if (applyColor && apply) {
                currentSub.subtitle.provideNewMarkUp(boldness, italic, underLine, color!!)
            } else if (apply && !applyColor){
                currentSub.subtitle.provideNewMarkUp(boldness, italic, underLine, "")
        }
    }
    }
    private fun hideOtherButtons(value : Int) {
        binding.browseNetwork.visibility = value
        binding.createMarkupForEntireSubtitle.visibility = value
        binding.resetColorCodes.visibility = value
        binding.switchDayNight.visibility = value
    }
    private fun changeCharset() {
        val charsetRecyclerLayout = binding.charsetRecyclerLayout
        when (CharsetChange.charsetAdapterVisible) {
            View.GONE      -> { binding.resultView.visibility = View.GONE
                initializeRecycler()
                hideOtherButtons(View.GONE) }
            View.VISIBLE   ->  { charsetRecyclerLayout.visibility = View.GONE
                hideOtherButtons(View.VISIBLE)   }
            View.INVISIBLE -> { binding.resultView.visibility = View.GONE
                initializeRecycler()
                hideOtherButtons(View.GONE)
            }
        }
        CharsetChange.resultViewVisible = binding.resultView.visibility
        CharsetChange.charsetAdapterVisible = binding.charsetRecyclerLayout.visibility

    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdvancedBinding.inflate(inflater, container, false)
        val view = binding.root
        binding.browseNetwork.setOnClickListener {
            val action = FragmentAdvancedDirections.actionFragmentAdvancedToFragmentSMBExplorer(
                savingLocation = false,
                listAllFiles = true,
                extensions = arrayOf(),
                startLocation = "",
                request = "",
                showToolbar = true
            )
            view.findNavController().navigate(action)
        }
        binding.createMarkupForEntireSubtitle.setOnClickListener {
            createMarkupForEntireSubtitle()
        }
        binding.resetColorCodes.setOnClickListener {
            confirmDeletion()
        }
        binding.loadIntoSubtitle.setOnClickListener{
            try {
                reloadNewSubtitle(CharsetChange.resultSubtitle, CharsetChange.chosenCharset)
                CharsetChange.reset(CharsetChange.chosenCharset)
            } catch (e: NoSubtitlesFoundException) {
                Toast.makeText(requireContext(), getString(R.string.no_subs_were_found_in_this_file), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), getString(R.string.an_unknown_error_occurred), Toast.LENGTH_SHORT).show()
            }
        }
        binding.switchDayNight.setOnClickListener {
            when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
                Configuration.UI_MODE_NIGHT_YES -> {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    currentSub.backupNightTheme(this, false) }
                Configuration.UI_MODE_NIGHT_NO -> {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    currentSub.backupNightTheme(this, true)  }
            }
        }
        binding.charsetRecyclerLayout.visibility = CharsetChange.charsetAdapterVisible
        binding.resultView.visibility = CharsetChange.resultViewVisible
        if (CharsetChange.charsetAdapterVisible == View.VISIBLE) {
            initializeRecycler()
            hideOtherButtons(View.GONE)
        } else {
            hideOtherButtons(View.VISIBLE)
        }
        if (CharsetChange.resultSubtitle != "" && binding.resultView.visibility == View.VISIBLE) {
            placeText(CharsetChange.resultSubtitle)
            hideOtherButtons(View.GONE)
        }
        val charsetText = getString(R.string.current_selected_charset) + " ${currentSub.subtitle.usedCharSet}"
        binding.charsetInfo.text = charsetText
        binding.changeCharset.setOnClickListener {
            changeCharset()
        }
        toolbar = ToolBarHelper()
        toolbar.getToolbar(this)
        toolbar.hideItems(listOf(R.id.undo_toolbar, R.id.redo_toolbar))
        toolbar.addSpannedDescription(Html.fromHtml(getString(R.string.advanced_extra)))
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