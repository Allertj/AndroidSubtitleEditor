package com.subeditor.android_subtitle_editor

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.CharacterStyle
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.util.AttributeSet
import android.util.TypedValue
import android.view.*
import androidx.fragment.app.Fragment
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.text.HtmlCompat
import androidx.core.text.toSpannable
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResult
import androidx.navigation.fragment.findNavController
import com.subeditor.android_subtitle_editor.databinding.FragmentEditSingleBinding

object CurrentLine {
    lateinit var currentLine : Spannable
}

class SpanPipeLine(spannable: Spannable) {
    private val colors = arrayListOf<String?>()
    private val underline = arrayListOf<Boolean>()
    private val bold = arrayListOf<Boolean>()
    private var italic = arrayListOf<Boolean>()
    private var allUsedColors = mutableSetOf<String>()
    private var plainText = spannable.toString().toSpannable()
    init {
        cleanUpSpans(spannable)
    }
    private fun askObject(asked : String) : CharacterStyle {
        return when (asked) {
            "StyleSpan(Typeface.BOLD)" -> StyleSpan(Typeface.BOLD)
            "StyleSpan(Typeface.ITALIC)" -> StyleSpan(Typeface.ITALIC)
            "UnderlineSpan()" -> UnderlineSpan()
            else -> ForegroundColorSpan(Color.parseColor(asked))
        }
    }
    private fun getIndexes(array: ArrayList<Boolean>): List<Pair<Int, Int>> {
        val starts = arrayListOf<Int>()
        val ends = arrayListOf<Int>()
        array.forEachIndexed { index, item ->
            if (item) {
                if (index == 0 || !array[index - 1]) {
                    starts.add(index)
                }
            }
            if (!item && index != 0) {
                if (array[index - 1]) {
                    ends.add(index)
                }
            }
        }
        if (array[array.lastIndex]) {  ends.add(array.lastIndex+1) }
        return starts.zip(ends)
    }

    private fun consignToPairs(characterStyle: String, array: ArrayList<Boolean>) {
        if (!array.contains(true)) {
            return
        }
        getIndexes(array).forEach {
            val new = askObject(characterStyle)
            plainText.setSpan(new, it.first, it.second, Spannable.SPAN_COMPOSING)
        }
    }
    private fun cleanUpSpans(spannable: Spannable) {
        spannable.forEachIndexed{ index: Int, _: Char ->
            colors.add(null)
            underline.add(false)
            bold.add(false)
            italic.add(false)
            val allFoundSpans = spannable.getSpans(index, index, ForegroundColorSpan::class.java)
            allFoundSpans.forEach {
                colors[index] =  "#" + Integer.toHexString(it.foregroundColor).drop(2)
                if (allFoundSpans.size == 1) {
                    val dd = spannable.getSpanEnd(it)
                    if (index >= dd) {
                        colors[dd] = null
                    }
                }
            }
            val underLineSpans = spannable.getSpans(index, index, UnderlineSpan::class.java)
            underLineSpans.forEach {
                underline[index] = true
                if (underLineSpans.size == 1) {
                    val dd = spannable.getSpanEnd(it)
                    if (index >= dd) {
                        underline[dd] = false
                    }
                }
            }
            val otherStyleSpans = spannable.getSpans(index, index, StyleSpan::class.java)
            otherStyleSpans.forEach { currentSpan ->
                if (currentSpan.style == Typeface.ITALIC) {
                        italic[index] = true
                        val italicSpans = otherStyleSpans.filter { it.style == Typeface.ITALIC  }
                        if (italicSpans.size == 1) {
                            val ff = spannable.getSpanEnd(currentSpan)
                            if (index >= ff) {
                                italic[ff] = false
                            }
                        }
                    } else if (currentSpan.style == Typeface.BOLD) {
                    bold[index] = true
                    val boldSpans = otherStyleSpans.filter { it.style == Typeface.BOLD  }
                    if (boldSpans.size == 1) {
                        val ff = spannable.getSpanEnd(currentSpan)
                        if (index >= ff) {
                            bold[ff] = false
                        }    }
                    }
            }
        }
        val newAllUsedColors = mutableSetOf<String>()
        colors.forEach {
            if (it != null) {
                newAllUsedColors.add(it)
            }
        }
        allUsedColors = newAllUsedColors
    }

    private fun createBooleanArrayColors(): ArrayList<Pair<String, ArrayList<Boolean>>> {
        val allUsedColorsArrays = arrayListOf<Pair<String, ArrayList<Boolean>>>()
        allUsedColors.forEach{ color ->
            val results = arrayListOf<Boolean>()
            colors.forEach { results.add(it == color) }
            allUsedColorsArrays.add(Pair(color, results))
        }
        return allUsedColorsArrays
    }
    private fun createSpan(): ArrayList<Pair<String, ArrayList<Boolean>>> {
        val chars = arrayListOf<Pair<String, ArrayList<Boolean>>>()
        if (bold.any { true })
            chars.add(Pair("StyleSpan(Typeface.BOLD)", bold))
        if (italic.any { true })
            chars.add(Pair("StyleSpan(Typeface.ITALIC)", italic))
        if (underline.any { true })
            chars.add(Pair("UnderlineSpan()", underline))
        return chars
    }
    private fun cleanUpSpan(spannable: Spannable): String {
        val removalMap = mapOf("""<span style="color:#""" to """<font color="#""",
            """</span>""" to  """</font>""",
            """<p dir="ltr">""" to "",
            """</p>""" to "",
            """;">""" to """">""",
            "<br>" to "")
        var result = HtmlCompat.toHtml(spannable, HtmlCompat.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE)
        removalMap.keys.forEach {
            result = result.replace(it, removalMap[it]!!)
        }
        return result
    }
    fun build(): String {
        val lists = createSpan() + createBooleanArrayColors()
        lists.forEach {
            consignToPairs(it.first, it.second)
        }
        return cleanUpSpan(plainText).trim()
    }
}

class TestEditText : androidx.appcompat.widget.AppCompatEditText {
    constructor(context: Context?) : super(context!!)
    constructor(context: Context?, attrs: AttributeSet?) : super(context!!, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context!!, attrs, defStyleAttr)
    private var currentStart = 0
    private var currentEnd = 0
    lateinit var subtitle: Subtitle
    private var allAppliedSpans = arrayListOf<Any>()
    override fun onTextChanged(
        text: CharSequence?,
        start: Int,
        lengthBefore: Int,
        lengthAfter: Int
    ) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
        CurrentLine.currentLine = text as Spannable
    }
    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        currentStart = selStart
        currentEnd = selEnd
    }
    fun resetAllMarkup(spannable: Spannable): Spannable {
        return spannable.toString().toSpannable()
    }
    private fun findLength(spannable: Spannable, style: Any): Boolean {
        if (style.javaClass.simpleName == "ForegroundColorSpan") {
            val sss =  spannable.getSpans(currentStart, currentStart, ForegroundColorSpan::class.java).isNotEmpty()
            val ssss = spannable.getSpans(currentEnd, currentEnd, ForegroundColorSpan::class.java).isNotEmpty()
            return sss && ssss
        }
        if (style.javaClass.simpleName == "UnderlineSpan") {
            val sss =  spannable.getSpans(currentStart, currentStart, UnderlineSpan::class.java).isNotEmpty()
            val ssss = spannable.getSpans(currentEnd, currentEnd, UnderlineSpan::class.java).isNotEmpty()
            return sss && ssss
        }
        if (style.javaClass.simpleName == "StyleSpan" && (style as StyleSpan).style == Typeface.BOLD) {
            val sss = spannable.getSpans(currentStart, currentStart, StyleSpan::class.java)
                .any { it.style == Typeface.BOLD }
            val ssss = spannable.getSpans(currentEnd, currentEnd, StyleSpan::class.java)
                .any { it.style == Typeface.BOLD }
            return sss && ssss
        }
        if (style.javaClass.simpleName == "StyleSpan" && (style as StyleSpan).style == Typeface.ITALIC) {
            val sss = spannable.getSpans(currentStart, currentStart, StyleSpan::class.java)
                .any { it.style == Typeface.ITALIC }
            val ssss = spannable.getSpans(currentEnd, currentEnd, StyleSpan::class.java)
                .any { it.style == Typeface.ITALIC }
            return sss && ssss
        }
        return false
    }
    fun addMarkUp(spannable: Spannable, style: Any) {
        allAppliedSpans.add(style)
        if (currentEnd == 0 && currentStart == 0 && this.text?.length!! > 0) {
            spannable.setSpan(style, currentStart, this.text?.length!!, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
        } else {
            if (currentEnd == currentStart) {

                spannable.setSpan(style, 0, this.text?.length!!, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
            } else {
                if (findLength(spannable, style)) {
                       spannable.setSpan(style, currentStart, currentEnd-1, Spannable.SPAN_MARK_MARK)
                   } else {
                       spannable.setSpan(style, currentStart, currentEnd, Spannable.SPAN_MARK_MARK)
                   }
            }
        }
    }
    fun getSpan(): Spannable? {
        return this.text?.toSpannable()
    }
}

class ConfirmDeletionFragment : DialogFragment() {
    var index = 0
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        if (savedInstanceState != null) {
            index = savedInstanceState.getInt("Index")
        }
        return android.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.delete_this_subtitle))
            .setMessage(CurrentLine.currentLine)
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                setFragmentResult("Deletion", bundleOf("Delete" to true))
            }
            .setNegativeButton(getString(R.string.no)) { _, _ ->
                setFragmentResult("Deletion", bundleOf("Delete" to false))
            }.show()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("Index", index)
    }
}
class EditTextFragment : DialogFragment() {
    var index = 0
    var text1 = ""
    private val currentSub: CurrentSub by activityViewModels()
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        if (savedInstanceState != null) {
            index = savedInstanceState.getInt("Index")
            text1 = savedInstanceState.getString("TextOfLine", "")
        }
        val textBox = EditText(requireContext())
        textBox.text.insert(0,  text1)
        val dd = android.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.edit_text))
            .setView(textBox)
            .setPositiveButton(getString(R.string.change)) { _, _ ->
                val newString = textBox.text.toString()
                val ddd = HtmlCompat.fromHtml(newString, HtmlCompat.FROM_HTML_MODE_COMPACT).trim()
                CurrentLine.currentLine = ddd as Spannable
                currentSub.subtitle.insertTextIntoSubtitle(index, newString)
            }
            .setNegativeButton(getString(R.string.cancel)) { _, _ -> run {}  }
        return dd.show()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("Index", index)
        outState.putString("TextOfLine", text1)
        super.onSaveInstanceState(outState)
    }
}

class FragmentEditSingle : Fragment() {
    private var index = 0
    var color : String = "#000000"
    private var _binding: FragmentEditSingleBinding? = null
    private val binding get() = _binding!!
    lateinit var toolbar: ToolBarHelper
    private val currentSub: CurrentSub by activityViewModels()
    private var deleted = false
    private lateinit var shiftNotPossibleToast : Toast
    private fun getColorFromTheme(): String {
        val typedValue = TypedValue()
        requireContext().theme.resolveAttribute(R.attr.colorPrimary, typedValue, true)
        return "#" + Integer.toHexString(typedValue.data).substring(2)
    }
    private fun updateText(){
        (binding.selectedSubtitle as TextView).text = CurrentLine.currentLine
        binding.startTimeOfSelected.text = currentSub.subtitle.getStartTimeAsString(index)
        binding.endTimeOfSelected.text = currentSub.subtitle.getEndTimeAsString(index)
        binding.chosenColor.backgroundTintList = ColorStateList.valueOf(Color.parseColor(color))
    }
    private fun editTextOfSubtitle(index: Int) {
        val editTextFragment = EditTextFragment()
        val text1 = SpanPipeLine(binding.selectedSubtitle.getSpan()!!).build()
        editTextFragment.index = index
        editTextFragment.text1 = text1
        editTextFragment.show(childFragmentManager, "")
        updateText()
    }
    private fun moveSingleSubtitle(movement: Int) {
        val hasShifted = currentSub.subtitle.shiftIndividualSubtitle(index, movement)
        if (hasShifted) {
            updateText()
        } else {
            shiftNotPossibleToast = Toast.makeText(requireContext(), getString(R.string.individual_shift_not_possible), Toast.LENGTH_SHORT)
            shiftNotPossibleToast.show()
        }
    }
    private fun changeLengthOfSubtitle(extraLength: Int) {
        val hasChanged = currentSub.subtitle.changeLengthOfIndividualSubtitle(index, extraLength)
        if (hasChanged) {
            updateText()
        } else {
            shiftNotPossibleToast = Toast.makeText(requireContext(), getString(R.string.individual_length_change_not_possible), Toast.LENGTH_SHORT)
            shiftNotPossibleToast.show()
        }
    }
    private fun confirmDeletion() {
        val confirmDeletion = ConfirmDeletionFragment()
        confirmDeletion.index = index
        confirmDeletion.show(childFragmentManager, "")
        childFragmentManager.setFragmentResultListener("Deletion", this)
        { _ , bundle ->
            val resultReceived = bundle.getBoolean("Delete")
            if (resultReceived) {
                currentSub.subtitle.removeAtBySwipe(index)
                deleted = true
                val action = FragmentEditSingleDirections.returnToEdit()
                findNavController().navigate(action)
            }
        }
    }
    private fun restoreText() {
        val stringAsHtml = currentSub.subtitle.getJustTheText(index).split("\n").joinToString("<br>")
        val ddd = HtmlCompat.fromHtml(stringAsHtml, HtmlCompat.FROM_HTML_MODE_COMPACT).trim()
        CurrentLine.currentLine = ddd as SpannableStringBuilder
        binding.selectedSubtitle.text = CurrentLine.currentLine as SpannableStringBuilder
    }
    private fun pickColor() {
        PickColorFragment().show(childFragmentManager, "")
        childFragmentManager.setFragmentResultListener("requestColor", this)
        { _, bundle ->
            val response = bundle.getString("chosenColor")
            if (response != null) {
                color = response
                updateText()
            }
        }
    }


    private fun addTextToSubtitle() {
        val newSpan = SpanPipeLine(binding.selectedSubtitle.getSpan()!!).build()
        currentSub.subtitle.insertTextIntoSubtitle(index, newSpan)
    }
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        index = FragmentEditSingleArgs.fromBundle(requireArguments()).index
        _binding = FragmentEditSingleBinding.inflate(inflater, container, false)
        val view = binding.root
        color = getColorFromTheme()
        val stringAsHtml = currentSub.subtitle.getJustTheText(index).split("\n").joinToString("<br>")
        val ddd = HtmlCompat.fromHtml(stringAsHtml, HtmlCompat.FROM_HTML_MODE_COMPACT).trim()
        CurrentLine.currentLine = ddd as SpannableStringBuilder
        updateText()
        shiftNotPossibleToast = Toast.makeText(requireContext(), getString(R.string.individual_shift_not_possible), Toast.LENGTH_SHORT)
        binding.shiftSubtitle01sBackwards.setOnClickListener    { moveSingleSubtitle(-100) }
        binding.shiftSubtitle01sForwards.setOnClickListener    { moveSingleSubtitle(100) }
        binding.shiftSubtitle1sBackwards.setOnClickListener    { moveSingleSubtitle(-1000) }
        binding.shiftSubtitle1sForward.setOnClickListener    { moveSingleSubtitle(1000) }
        binding.chosenColor.setOnClickListener {
            pickColor()
        }
        binding.deleteSubtitle.setOnClickListener {
            confirmDeletion()
        }
        binding.editTextOfSubtitle.setOnClickListener {
            editTextOfSubtitle(index)
            updateText()
            addTextToSubtitle()
        }
        binding.addBoldness.setOnClickListener {
            binding.selectedSubtitle.addMarkUp(CurrentLine.currentLine, StyleSpan(Typeface.BOLD))
            updateText()
            addTextToSubtitle()
        }
        binding.addItalics.setOnClickListener {
            binding.selectedSubtitle.addMarkUp(CurrentLine.currentLine, StyleSpan(Typeface.ITALIC))
            updateText()
            addTextToSubtitle()
        }
        currentSub.subtitle.subtitleChange.observe(viewLifecycleOwner) {
            if (it?.type == SubtitleChange.CHANGE || it?.singleOrComplete == SubtitleChange.COMPLETE) {
                restoreText()
            } else {
                if (it?.type != SubtitleChange.DELETION) {
                    updateText()
                }
            }
            currentSub.backupSession(this, it)
        }
        binding.addUnderline.setOnClickListener {
            binding.selectedSubtitle.addMarkUp(CurrentLine.currentLine, UnderlineSpan())
            updateText()
            addTextToSubtitle()
        }
        binding.addColor.setOnClickListener {
            val styleSpan = ForegroundColorSpan(Color.parseColor(color))
            binding.selectedSubtitle.addMarkUp(CurrentLine.currentLine, styleSpan)
            addTextToSubtitle()
            updateText()
        }
        binding.resetStyling.setOnClickListener {
            CurrentLine.currentLine = binding.selectedSubtitle.resetAllMarkup(binding.selectedSubtitle.getSpan()!!)
            updateText()
            addTextToSubtitle()
        }
        binding.addSubtitleAfterThisOne.setOnClickListener{
            val placed = currentSub.subtitle.addSubtitleLine(index, getString(R.string.default_subtitle_text))
            if (placed) {
                val askConfirm = AskConfirmationFragment()
                askConfirm.message = getString(R.string.edit_new_subtitle)
                askConfirm.show(childFragmentManager, "")
                childFragmentManager.setFragmentResultListener("requestConfirm", this)
                { _, bundle ->
                    val resultReceived = bundle.getBoolean("Confirmed")
                    if (resultReceived) {
                        val action = FragmentEditDirections.actionReloadEditSingle(index + 1)
                        findNavController().navigate(action)
                    }
                }
            } else {
                Toast.makeText(requireContext(), getString(R.string.subtitle_not_placed), Toast.LENGTH_SHORT).show()
            }
        }
        binding.decreaseLengthOfSub.setOnClickListener    { changeLengthOfSubtitle(-100) }
        binding.increaseLengthOfSub.setOnClickListener    { changeLengthOfSubtitle(100) }
        toolbar = ToolBarHelper()
        toolbar.getToolbar(this)
        toolbar.hideItems(listOf(R.id.save_toolbar))
        toolbar.addDescription(getString(R.string.FragmentEditSingle))
        return view
    }

    override fun onResume() {
        toolbar.showToolbar()
        super.onResume()
    }

    override fun onStop() {
        if (!deleted) {
            val newSpan = SpanPipeLine(binding.selectedSubtitle.getSpan()!!).build()
            currentSub.subtitle.insertTextIntoSubtitle(index, newSpan)
        }
        super.onStop()
    }
    override fun onDetach() {
        if (this::toolbar.isInitialized) {
            toolbar.hideToolbar()
        }
        if (this::shiftNotPossibleToast.isInitialized) {
            shiftNotPossibleToast.cancel()
        }
        super.onDetach()
    }
    override fun onDestroyView() {
        if (this::shiftNotPossibleToast.isInitialized) {
            shiftNotPossibleToast.cancel()
        }
        _binding = null
        super.onDestroyView()
    }
}