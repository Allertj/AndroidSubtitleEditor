package com.subeditor.android_subtitle_editor

import android.app.Dialog
import android.os.Bundle
import android.text.Spanned
import android.util.TypedValue
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.AlphaAnimation
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.core.text.HtmlCompat
import androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY
import androidx.core.text.toSpanned
import androidx.core.view.children
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.subeditor.android_subtitle_editor.databinding.ToolbarBinding

class HelpDialog: DialogFragment() {
    var message = "".toSpanned()
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        if (savedInstanceState != null) {
            message = HtmlCompat.fromHtml(savedInstanceState.getString("Text1")!!, FROM_HTML_MODE_LEGACY)
        }
        val dd = android.app.AlertDialog.Builder(requireContext())
            .setMessage(message)
            .setPositiveButton(getString(R.string.ok)) { _, _ ->}
        return dd.show()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        val sss = HtmlCompat.toHtml(message, HtmlCompat.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE)
        outState.putString("Text1", sss)
        super.onSaveInstanceState(outState)
    }
}

class ToolBarHelper {
    lateinit var fragment: Fragment
    private val values = mutableMapOf<String, Spanned>()
    private lateinit var currentSub: CurrentSub
    private lateinit var binding: ToolbarBinding
    fun addSpannedDescription(description: Spanned) {
        values[fragment.javaClass.simpleName] = description
    }
    fun addDescription(description: String){
        values[fragment.javaClass.simpleName] = description.toSpanned()
    }
    private fun displayHelp() {
        var message = fragment.getString(R.string.question_mark).toSpanned()
        if (values.containsKey(fragment.javaClass.simpleName)) {
            message = values[fragment.javaClass.simpleName]!!
        }
        val helpDialog = HelpDialog()
        helpDialog.message = message
        helpDialog.show(fragment.childFragmentManager, "")

    }
    fun hideItems(itemsToHide: List<Int>) {
        itemsToHide.forEach {
            fragment.activity?.findViewById<FrameLayout>(it)?.visibility = View.GONE
        }
    }
    fun hideToolbarCompletely() {
        binding.root.visibility = View.GONE
    }
    fun showToolbarCompletely(){
        binding.root.visibility = View.VISIBLE
    }
    fun showToolbar() {
        val toolBarView = binding.toolbar
        toolBarView.visibility = View.VISIBLE
        val animation = AlphaAnimation(0f, 1f)
        animation.duration = 100.toLong()
        animation.interpolator = DecelerateInterpolator()
        toolBarView.animation = animation
        animation.start()
    }
    fun hideToolbar() {
        val toolBarView = binding.toolbar
        toolBarView.visibility = View.GONE
        val animation = AlphaAnimation(1f, 0f)
        animation.duration = 100.toLong()
        animation.interpolator = AccelerateInterpolator()
        toolBarView.animation = animation
        animation.start()
    }
    fun getToolbar(currentFragment: Fragment) {
        fragment = currentFragment
        binding = ToolbarBinding.bind(fragment.requireActivity().findViewById(R.id.toolbar_holder)!!)
        binding.root.visibility = View.VISIBLE
        val new : CurrentSub by fragment.activityViewModels()
        currentSub = new
        binding.helpToolbar.visibility = View.GONE
        val toolbarLayout = binding.toolbar
        toolbarLayout.visibility = View.VISIBLE
        toolbarLayout.children.forEach {
            it.visibility = View.VISIBLE
        }
        currentSub.subtitle.subtitleChange.observe(fragment.viewLifecycleOwner) {
            updateToolbar()
            currentSub.backupSession(fragment, it)
        }
        binding.mainMenuToolbarActionBackground.setOnClickListener {
            if (fragment.javaClass.simpleName == "FragmentEditSingle") {
                fragment.findNavController().popBackStack(R.id.fragmentEdit, false)
            } else {
                fragment.findNavController().popBackStack(R.id.fragmentMainMenu, false)
            }
        }
        binding.helpToolbarActionBackground.setOnClickListener {
            displayHelp()
        }
        binding.viewToolbarActionBackground.setOnClickListener {
            fragment.findNavController().navigate(R.id.fragmentViewSubtitle)
        }
        binding.playToolbarActionBackground.setOnClickListener {
            if (currentSub.videoLocation == null || currentSub.actualVideoLocation == "") {
                fragment.findNavController().navigate(R.id.fragmentSelectVideo)
            } else {
                if (currentSub.videoLocatedAt == CurrentSub.LAN) {
                    currentSub.initiateServer(fragment)
                } else {
                    fragment.findNavController().navigate(R.id.fragmentNewPlayer)
                }
            }
        }
        binding.saveToolbarActionBackground.setOnClickListener {
            fragment.findNavController().navigate(R.id.fragmentSaveSubtitle)
        }
    }

    private fun updateToolbar() {
        val typedValue = TypedValue()
        fragment.activity?.theme?.resolveAttribute(R.attr.actionBarItemBackground, typedValue, true)
        val redoToolBar = binding.redoToolbar
        val redoAction = binding.redoToolbarActionBackground
        if (!currentSub.subtitle.redoPossible) {
            redoToolBar.setBackgroundResource(R.drawable.icon_redo_grey)
            redoAction.setBackgroundResource(0)
        } else {
            if (typedValue.resourceId != 0) {  redoAction.setBackgroundResource(typedValue.resourceId) }
            redoToolBar.setBackgroundResource(R.drawable.icon_redo)
            redoAction.setOnClickListener {
                if (currentSub.subtitle.undoneActions.isNotEmpty()) {
                    currentSub.subtitle.redoLastAction()
                    updateToolbar()
                }
            }
        }
        val undoToolbar = binding.undoToolbar
        val undoAction = binding.undoToolbarActionBackground
        if (currentSub.subtitle.actions.isEmpty()) {
            undoToolbar.setBackgroundResource(R.drawable.icon_undo_grey)
            undoAction.setBackgroundResource(0)
        } else {
            if (typedValue.resourceId != 0) {  undoAction.setBackgroundResource(typedValue.resourceId) }
            undoToolbar.setBackgroundResource(R.drawable.icon_undo)
            undoAction.setOnClickListener {
                if (currentSub.subtitle.actions.isNotEmpty()) {
                    currentSub.subtitle.undoLastAction()
                    updateToolbar()
                }
            }
        }
    }
}