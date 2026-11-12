package com.localgrammar.fix

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.SystemClock
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CorrectionScreen(
    context: Context,
    private val scope: LifecycleCoroutineScope,
    initialText: String = "",
    private val canReplaceSelection: Boolean = false,
    private val onReplaceSelection: (String) -> Unit = {}
) : ScrollView(context) {
    private val input = EditText(context)
    private val output = TextView(context)
    private val inputCount = TextView(context)
    private val outputCount = TextView(context)
    private val status = TextView(context)
    private val progress = ProgressBar(context)
    private val fixButton = Button(context)
    private val copyButton = Button(context)
    private val replaceButton = Button(context)

    init {
        setBackgroundColor(0xFFF7F8FC.toInt())
        isFillViewport = true

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(32))
        }
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        content.addView(label(if (canReplaceSelection) "Fix selected text" else "Offline Grammar Fix", 24f, true))
        content.addView(label("Your text is checked on this device.", 14f), matchWidth().withTopMargin(4))

        content.addView(label("Text to correct", 16f, true), matchWidth().withTopMargin(24))
        input.apply {
            hint = "Type or paste text here"
            setText(initialText)
            gravity = Gravity.TOP or Gravity.START
            minLines = 5
            maxLines = 10
            textSize = 16f
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundColor(0xFFFFFFFF.toInt())
        }
        content.addView(input, matchWidth().withTopMargin(8))
        content.addView(inputCount, matchWidth().withTopMargin(6))
        inputCount.textSize = 13f
        inputCount.setTextColor(0xFF596274.toInt())

        fixButton.text = "Fix Grammar"
        fixButton.isAllCaps = false
        content.addView(fixButton, matchWidth().withTopMargin(14))
        progress.visibility = View.GONE
        content.addView(progress, LinearLayout.LayoutParams(dp(32), dp(32)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(8)
        })

        status.textSize = 14f
        status.setTextColor(0xFF43506A.toInt())
        status.visibility = View.GONE
        content.addView(status, matchWidth().withTopMargin(8))

        content.addView(label("Corrected text", 16f, true), matchWidth().withTopMargin(22))
        output.apply {
            textSize = 16f
            setTextColor(0xFF1D2638.toInt())
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundColor(0xFFFFFFFF.toInt())
            setTextIsSelectable(true)
            minHeight = dp(100)
            text = "Corrected text will appear here."
        }
        content.addView(output, matchWidth().withTopMargin(8))
        content.addView(outputCount, matchWidth().withTopMargin(6))
        outputCount.textSize = 13f
        outputCount.setTextColor(0xFF596274.toInt())
        outputCount.text = "Words: 0  ·  Characters: 0  ·  Time: —"

        copyButton.text = "Copy corrected text"
        copyButton.isAllCaps = false
        copyButton.isEnabled = false
        content.addView(copyButton, matchWidth().withTopMargin(14))

        if (canReplaceSelection) {
            replaceButton.text = "Replace selected text"
            replaceButton.isAllCaps = false
            replaceButton.isEnabled = false
            content.addView(replaceButton, matchWidth().withTopMargin(8))
            replaceButton.setOnClickListener {
                val corrected = output.text.toString()
                if (copyButton.isEnabled && corrected.isNotBlank()) onReplaceSelection(corrected)
            }
        }

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                inputCount.text = counts(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        inputCount.text = counts(initialText)

        copyButton.setOnClickListener {
            val corrected = output.text.toString()
            if (!copyButton.isEnabled || corrected.isBlank()) return@setOnClickListener
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Corrected text", corrected))
            Toast.makeText(context, "Corrected text copied", Toast.LENGTH_SHORT).show()
        }

        fixButton.setOnClickListener { runCorrection() }
    }

    private fun runCorrection() {
        val text = input.text?.toString().orEmpty()
        if (text.isBlank()) {
            showStatus("Enter or paste text first.", error = true)
            return
        }

        fixButton.isEnabled = false
        fixButton.text = "Fixing…"
        progress.visibility = View.VISIBLE
        showStatus("Checking grammar locally…", error = false)
        copyButton.isEnabled = false
        if (canReplaceSelection) replaceButton.isEnabled = false
        output.text = ""
        outputCount.text = "Words: 0  ·  Characters: 0  ·  Time: —"
        val startedAt = SystemClock.elapsedRealtime()

        scope.launch {
            try {
                val corrected = withContext(Dispatchers.IO) {
                    LocalGrammarCorrector.correct(text)
                }
                val elapsedSeconds = (SystemClock.elapsedRealtime() - startedAt) / 1000.0
                output.text = corrected
                outputCount.text = "${counts(corrected)}  ·  Time: ${"%.2f".format(java.util.Locale.US, elapsedSeconds)} s"
                copyButton.isEnabled = corrected.isNotBlank()
                if (canReplaceSelection) replaceButton.isEnabled = corrected.isNotBlank()
                showStatus("Grammar check complete.", error = false)
            } catch (failure: Throwable) {
                // Surface linkage/runtime failures too; the old selection action hid them.
                output.text = ""
                outputCount.text = "Words: 0  ·  Characters: 0  ·  Time: —"
                showStatus(
                    "Grammar check failed: ${failure.message ?: failure.javaClass.simpleName}",
                    error = true
                )
            } finally {
                fixButton.isEnabled = true
                fixButton.text = "Fix Grammar"
                progress.visibility = View.GONE
            }
        }
    }

    private fun showStatus(message: String, error: Boolean) {
        status.text = message
        status.setTextColor(if (error) 0xFFB42318.toInt() else 0xFF43506A.toInt())
        status.visibility = View.VISIBLE
    }

    private fun counts(text: String): String {
        val wordCount = text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
        return "Words: $wordCount  ·  Characters: ${text.length}"
    }

    private fun label(value: String, size: Float, bold: Boolean = false) = TextView(context).apply {
        text = value
        textSize = size
        setTextColor(0xFF182230.toInt())
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun matchWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun LinearLayout.LayoutParams.withTopMargin(margin: Int) = apply { topMargin = dp(margin) }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
