package com.localgrammar.fix

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FixTextActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val message = TextView(this).apply {
            text = "Checking grammar on this device…"
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(32, 24, 32, 24)
        }
        val progress = ProgressBar(this)
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(progress)
            addView(message)
        }
        setContentView(container)

        if (intent?.action != Intent.ACTION_PROCESS_TEXT) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        val selectedText = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty()
        val canReplace = !intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
        if (selectedText.isBlank() || !canReplace) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        lifecycleScope.launch {
            try {
                val corrected = withContext(Dispatchers.IO) {
                    LocalGrammarCorrector.correct(selectedText)
                }
                setResult(
                    Activity.RESULT_OK,
                    Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, corrected)
                )
            } catch (_: Exception) {
                setResult(Activity.RESULT_CANCELED)
            }
            finish()
        }
    }
}
