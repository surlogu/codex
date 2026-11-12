package com.localgrammar.fix

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope

class FixTextActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.action != Intent.ACTION_PROCESS_TEXT) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        val selectedText = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty()
        if (selectedText.isBlank()) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        val canReplace = !intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
        setContentView(
            CorrectionScreen(
                context = this,
                scope = lifecycleScope,
                initialText = selectedText,
                canReplaceSelection = canReplace,
                onReplaceSelection = { corrected ->
                    setResult(Activity.RESULT_OK, Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, corrected))
                    finish()
                }
            )
        )
    }
}
