package com.localgrammar.fix

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
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
    private val autoCorrectOnOpen: Boolean = false,
    private val onReplaceSelection: (String) -> Unit = {}
) : ScrollView(context) {
    private val primaryGreen = Color.rgb(22, 112, 76)
    private val ink = Color.rgb(26, 43, 37)
    private val mutedInk = Color.rgb(100, 115, 107)
    private val input = EditText(context)
    private val output = TextView(context)
    private val inputCount = TextView(context)
    private val outputCount = TextView(context)
    private val status = TextView(context)
    private val progress = ProgressBar(context)
    private val fixButton = Button(context)
    private val copyButton = Button(context)
    private val replaceButton = Button(context)
    private val writeTab = Button(context)
    private val resultTab = Button(context)
    private val writePanel = LinearLayout(context)
    private val resultPanel = LinearLayout(context)
    private var hasResult = false
    private val localModel = LocalOnDeviceModel(context)

    init {
        setBackgroundColor(Color.rgb(244, 248, 245))
        isFillViewport = true

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(40), dp(20), dp(32))
        }
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val appMark = ImageView(context).apply {
            setImageResource(R.drawable.ic_clearwrite_mark)
            setPadding(dp(1), dp(1), dp(1), dp(1))
            contentDescription = "ClearWrite logo"
        }
        header.addView(appMark, LinearLayout.LayoutParams(dp(48), dp(48)))
        val titleBlock = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        titleBlock.addView(label("ClearWrite", 24f, true))
        titleBlock.addView(
            label(
                if (canReplaceSelection) "Fix selected text privately"
                else "Private, offline grammar correction",
                13f
            ).apply { setTextColor(mutedInk) },
            matchWidth().withTopMargin(2)
        )
        header.addView(titleBlock, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(12)
        })
        content.addView(header)

        writePanel.orientation = LinearLayout.VERTICAL
        resultPanel.orientation = LinearLayout.VERTICAL
        val tabs = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(Color.rgb(230, 238, 232), dp(14))
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        writeTab.text = "Write"
        resultTab.text = "Result"
        writeTab.isAllCaps = false
        resultTab.isAllCaps = false
        styleTab(writeTab, selected = true)
        styleTab(resultTab, selected = false)
        listOf(writeTab, resultTab).forEach { tab ->
            tab.setOnClickListener { showResultTab(tab === resultTab) }
            tabs.addView(tab, LinearLayout.LayoutParams(0, dp(42), 1f))
        }
        content.addView(tabs, matchWidth().withTopMargin(18))
        content.addView(writePanel, matchWidth().withTopMargin(10))
        content.addView(resultPanel, matchWidth().withTopMargin(10))
        showResultTab(false)

        writePanel.addView(label(if (canReplaceSelection) "Selected text" else "Your text", 16f, true), matchWidth().withTopMargin(14))
        input.apply {
            hint = "Type or paste your text here…"
            setText(initialText)
            gravity = Gravity.TOP or Gravity.START
            minLines = 5
            maxLines = 12
            textSize = 17f
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setPadding(dp(16), dp(15), dp(16), dp(15))
            background = rounded(Color.WHITE, dp(16), Color.rgb(224, 234, 227), dp(1))
            setTextColor(ink)
            setHintTextColor(Color.rgb(150, 163, 155))
        }
        writePanel.addView(input, matchWidth().withTopMargin(10))
        writePanel.addView(inputCount, matchWidth().withTopMargin(7))
        inputCount.textSize = 13f
        inputCount.gravity = Gravity.END
        inputCount.setTextColor(mutedInk)

        fixButton.text = "Fix grammar"
        fixButton.isAllCaps = false
        styleButton(fixButton, primaryGreen, Color.WHITE)
        writePanel.addView(fixButton, matchWidth().withTopMargin(15).apply { height = dp(54) })
        progress.visibility = View.GONE
        writePanel.addView(progress, LinearLayout.LayoutParams(dp(32), dp(32)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(8)
        })

        status.textSize = 14f
        status.setTextColor(0xFF43506A.toInt())
        status.visibility = View.GONE
        writePanel.addView(status, matchWidth().withTopMargin(8))

        resultPanel.addView(label("Corrected text", 16f, true))
        val changeLegend = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val legendSwatch = View(context).apply {
            background = rounded(Color.rgb(218, 246, 226), dp(4))
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
        resultPanel.addView(changeLegend, matchWidth().withTopMargin(6))
        output.apply {
            textSize = 17f
            setTextColor(ink)
            setPadding(dp(16), dp(15), dp(16), dp(15))
            background = rounded(Color.WHITE, dp(16), Color.rgb(224, 234, 227), dp(1))
            setLineSpacing(dp(3).toFloat(), 1f)
            setTextIsSelectable(true)
            minHeight = dp(100)
            text = "Corrected text will appear here."
        }
        resultPanel.addView(output, matchWidth().withTopMargin(8))
        resultPanel.addView(outputCount, matchWidth().withTopMargin(6))
        outputCount.textSize = 13f
        outputCount.gravity = Gravity.END
        outputCount.setTextColor(mutedInk)
        outputCount.text = "Words: 0  ·  Characters: 0  ·  Time: —"

        copyButton.text = "Copy text"
        copyButton.isAllCaps = false
        copyButton.isEnabled = false
        copyButton.alpha = 0.55f
        styleButton(copyButton, Color.WHITE, primaryGreen, primaryGreen)

        if (canReplaceSelection) {
            replaceButton.text = "Replace text"
            replaceButton.isAllCaps = false
            replaceButton.isEnabled = false
            replaceButton.alpha = 0.55f
            styleButton(replaceButton, primaryGreen, Color.WHITE)
            val actions = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            actions.addView(copyButton, LinearLayout.LayoutParams(0, dp(50), 1f))
            actions.addView(replaceButton, LinearLayout.LayoutParams(0, dp(50), 1f).apply {
                marginStart = dp(10)
            })
            resultPanel.addView(actions, matchWidth().withTopMargin(14))
            replaceButton.setOnClickListener {
                val corrected = output.text.toString()
                if (copyButton.isEnabled && corrected.isNotBlank()) onReplaceSelection(corrected)
            }
        } else {
            resultPanel.addView(copyButton, matchWidth().withTopMargin(14).apply { height = dp(50) })
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
        if (autoCorrectOnOpen) {
            post { runCorrection() }
        }
    }

    private fun runCorrection() {
        val text = input.text?.toString().orEmpty()
        if (text.isBlank()) {
            showStatus("Enter or paste text first.", error = true)
            return
        }

        fixButton.isEnabled = false
        fixButton.text = "Fixing…"
        fixButton.alpha = 0.75f
        progress.visibility = View.VISIBLE
        showStatus("Running the offline grammar model…", error = false)
        copyButton.isEnabled = false
        copyButton.alpha = 0.55f
        if (canReplaceSelection) {
            replaceButton.isEnabled = false
            replaceButton.alpha = 0.55f
        }
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
                copyButton.alpha = if (copyButton.isEnabled) 1f else 0.55f
                if (canReplaceSelection) {
                    replaceButton.isEnabled = corrected.isNotBlank()
                    replaceButton.alpha = if (replaceButton.isEnabled) 1f else 0.55f
                }
                hasResult = corrected.isNotBlank()
                resultTab.isEnabled = hasResult
                showResultTab(hasResult)
                showStatus("Grammar check complete.", error = false)
            } catch (failure: Throwable) {
                // Surface linkage/runtime failures too; the old selection action hid them.
                output.text = ""
                outputCount.text = "Words: 0  ·  Characters: 0  ·  Time: —"
                hasResult = false
                resultTab.isEnabled = false
                showResultTab(false)
                showStatus(
                    "Grammar check failed: ${failure.message ?: failure.javaClass.simpleName}",
                    error = true
                )
            } finally {
                fixButton.isEnabled = true
                fixButton.text = "Fix grammar"
                fixButton.alpha = 1f
                progress.visibility = View.GONE
            }
        }
    }

    fun close() = localModel.close()

    private fun showResultTab(showResult: Boolean) {
        val show = showResult && hasResult
        writePanel.visibility = if (show) View.GONE else View.VISIBLE
        resultPanel.visibility = if (show) View.VISIBLE else View.GONE
        styleTab(writeTab, selected = !show)
        styleTab(resultTab, selected = show)
    }

    private fun styleTab(button: Button, selected: Boolean) {
        button.background = rounded(
            if (selected) Color.WHITE else Color.TRANSPARENT,
            dp(11)
        )
        button.setTextColor(if (selected) primaryGreen else mutedInk)
        button.textSize = 14f
        button.setTypeface(button.typeface, if (selected) Typeface.BOLD else Typeface.NORMAL)
        button.isEnabled = selected || hasResult
        button.alpha = if (button.isEnabled) 1f else 0.55f
    }

    private fun showStatus(message: String, error: Boolean) {
        status.text = message
        status.setTextColor(if (error) 0xFFB42318.toInt() else mutedInk)
        status.visibility = View.VISIBLE
    }

    private fun counts(text: String): String {
        val wordCount = text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
        return "Words: $wordCount  ·  Characters: ${text.length}"
    }

    private fun rounded(fillColor: Int, radius: Int, strokeColor: Int? = null, strokeWidth: Int = 0) =
        GradientDrawable().apply {
            setColor(fillColor)
            cornerRadius = radius.toFloat()
            if (strokeColor != null && strokeWidth > 0) setStroke(strokeWidth, strokeColor)
        }

    private fun styleButton(button: Button, fillColor: Int, textColor: Int, strokeColor: Int? = null) {
        button.background = rounded(fillColor, dp(14), strokeColor, if (strokeColor == null) 0 else dp(1))
        button.setTextColor(textColor)
        button.textSize = 14f
        button.setTypeface(button.typeface, Typeface.BOLD)
        button.minHeight = dp(48)
        button.setPadding(dp(14), 0, dp(14), 0)
        button.elevation = dp(1).toFloat()
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
