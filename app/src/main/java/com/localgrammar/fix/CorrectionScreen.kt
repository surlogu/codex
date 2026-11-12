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
    private val localModel = LocalOnDeviceModel(context)

    init {
        setBackgroundColor(0xFFF7F8FC.toInt())
        isFillViewport = true

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(32))
        }
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        content.addView(label("ClearWrite", 26f, true))
        content.addView(
            label(
                if (canReplaceSelection) "Review the text you selected in another app."
                else "Private grammar correction that works offline.",
                14f
            ),
            matchWidth().withTopMargin(4)
        )

        content.addView(label(if (canReplaceSelection) "Selected text" else "Text to correct", 16f, true), matchWidth().withTopMargin(24))
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
        val changeLegend = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val legendSwatch = View(context).apply {
            setBackgroundColor(0xFFDAF6E2.toInt())
        }
        changeLegend.addView(legendSwatch, LinearLayout.LayoutParams(dp(14), dp(14)))
        changeLegend.addView(
            label("Green highlights show what changed", 12f).apply {
                setTextColor(0xFF596274.toInt())
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(7)
            }
        )
        content.addView(changeLegend, matchWidth().withTopMargin(6))
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

        content.addView(localModel.webView, LinearLayout.LayoutParams(dp(1), dp(1)))
        localModel.start()

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
        showStatus("Running the offline grammar model…", error = false)
        copyButton.isEnabled = false
        if (canReplaceSelection) replaceButton.isEnabled = false
        output.text = ""
        outputCount.text = "Words: 0  ·  Characters: 0  ·  Time: —"
        val startedAt = SystemClock.elapsedRealtime()

        scope.launch {
            try {
                val modelCorrected = localModel.correct(text) { message ->
                    post { showStatus(message, error = false) }
                }
                val localRulesCorrection = withContext(Dispatchers.IO) {
                    runCatching { LocalGrammarCorrector.correct(text) }.getOrNull()
                }
                val corrected = chooseCorrection(text, modelCorrected, localRulesCorrection)
                val elapsedSeconds = (SystemClock.elapsedRealtime() - startedAt) / 1000.0
                output.text = highlightCorrections(text, corrected)
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

    fun close() = localModel.close()

    private fun showStatus(message: String, error: Boolean) {
        status.text = message
        status.setTextColor(if (error) 0xFFB42318.toInt() else 0xFF43506A.toInt())
        status.visibility = View.VISIBLE
    }

    private fun counts(text: String): String {
        val wordCount = text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
        return "Words: $wordCount  ·  Characters: ${text.length}"
    }

    private fun chooseCorrection(input: String, modelText: String, rulesText: String?): String {
        if (modelText.isBlank()) return input
        val rules = rulesText?.takeIf { it.isNotBlank() } ?: input
        val fallback = if (rules != input) rules else input
        val modelHasSamePolarity = negationCount(input) == negationCount(modelText)
        if (!modelHasSamePolarity) return fallback

        val sourceContent = contentWords(input)
        if (sourceContent.isNotEmpty()) {
            val correctedContent = contentWords(modelText)
            val preserved = sourceContent.count { source ->
                correctedContent.any { corrected -> sameContentWord(source, corrected) }
            }
            if (preserved.toDouble() / sourceContent.size < 0.60) {
                return fallback
            }
        }

        val inputWords = input.trim().split(Regex("\\s+")).count { it.isNotBlank() }
        val modelWords = modelText.trim().split(Regex("\\s+")).count { it.isNotBlank() }
        val rulesWords = rules.trim().split(Regex("\\s+")).count { it.isNotBlank() }
        // Reject a short-input rewrite that changes the question or adds unrelated content.
        if (inputWords <= 8 && modelWords > 5 && modelWords > inputWords * 1.6 + 1) {
            return fallback
        }
        // Keep short spelling fixes when the model adds unnecessary wording.
        return if (rules != input && modelWords > rulesWords * 1.5) rules else modelText
    }

    private fun negationCount(text: String): Int = Regex(
        "(?i)\\b(?:not|no|never|neither|nor|don't|doesn't|didn't|isn't|aren't|wasn't|weren't|can't|couldn't|won't|wouldn't|shouldn't|mustn't)\\b"
    ).findAll(text).count()

    private fun contentWords(text: String): Set<String> {
        val commonWords = setOf(
            "a", "an", "the", "i", "me", "my", "mine", "we", "us", "our", "ours",
            "you", "your", "yours", "he", "him", "his", "she", "her", "hers", "it",
            "its", "they", "them", "their", "theirs", "this", "that", "these", "those",
            "am", "is", "are", "was", "were", "be", "been", "being", "do", "does",
            "did", "have", "has", "had", "will", "would", "can", "could", "should",
            "may", "might", "must", "to", "of", "in", "on", "at", "for", "from",
            "by", "with", "as", "and", "but", "or", "if", "than", "then", "when",
            "where", "who", "which", "what", "how", "there", "here", "not", "never",
            "neither", "nor", "don't", "doesn't", "didn't", "isn't", "aren't", "wasn't",
            "weren't", "can't", "couldn't", "won't", "wouldn't", "shouldn't", "mustn't"
        )
        return Regex("[\\p{L}\\p{N}]+(?:['’][\\p{L}\\p{N}]+)?")
            .findAll(text.lowercase(java.util.Locale.US))
            .map { it.value.replace('’', '\'') }
            .filterNot { it in commonWords }
            .map { word ->
                when {
                    word.length > 5 && word.endsWith("ing") -> word.dropLast(3)
                    word.length > 4 && word.endsWith("ed") -> word.dropLast(2)
                    word.length > 4 && word.endsWith("s") -> word.dropLast(1)
                    else -> word
                }
            }
            .toSet()
    }

    /** Treat a small spelling edit as the same content word, e.g. "leter" -> "letter". */
    private fun sameContentWord(source: String, corrected: String): Boolean {
        if (source == corrected) return true
        val longest = maxOf(source.length, corrected.length)
        if (longest < 4 || kotlin.math.abs(source.length - corrected.length) > 1) return false
        val allowedDistance = if (longest > 8) 2 else 1
        var previous = IntArray(corrected.length + 1) { it }
        for (i in source.indices) {
            val current = IntArray(corrected.length + 1)
            current[0] = i + 1
            var rowMinimum = current[0]
            for (j in corrected.indices) {
                val cost = if (source[i] == corrected[j]) 0 else 1
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + cost
                )
                rowMinimum = minOf(rowMinimum, current[j + 1])
            }
            if (rowMinimum > allowedDistance) return false
            previous = current
        }
        return previous[corrected.length] <= allowedDistance
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
