package com.subeditor.android_subtitle_editor

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import androidx.navigation.fragment.findNavController
import com.google.android.gms.ads.MobileAds
import com.subeditor.android_subtitle_editor.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class MainActivity : AppCompatActivity() {
    private val currentSub: CurrentSub by viewModels()
    private lateinit var binder: ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binder = ActivityMainBinding.inflate(layoutInflater)
        val view = binder.root
        MobileAds.initialize(this) {}
        setContentView(view)

    }
    override fun onNewIntent(intent: Intent?) {
        if (intent?.data != null) {
            try {
                val reloadDialog = ReloadDialog()
                val dd = supportFragmentManager.fragments.last().childFragmentManager.fragments[0]
                dd.childFragmentManager.beginTransaction()
                    .add(reloadDialog, "")
                    .commitAllowingStateLoss()
                dd.childFragmentManager.setFragmentResultListener("ReloadQuestion", this)
                { _, bundle ->
                    if (bundle["Reload"] as Boolean) {
                        currentSub.newUriIntent = intent.data
                        dd.findNavController().navigate(R.id.navigateToLoadAndLoad)
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this, e.stackTraceToString(), Toast.LENGTH_LONG).show()
            }
        }
        super.onNewIntent(intent)
    }
    override fun onBackPressed() {
        val dd = supportFragmentManager.fragments.last().childFragmentManager.fragments[0]
        if (dd.javaClass.simpleName == "FragmentLoadFile") {
            finish()
        }
        super.onBackPressed()
    }
}