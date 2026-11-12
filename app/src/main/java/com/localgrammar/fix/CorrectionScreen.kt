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
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.launch

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
    private val resultHeading = TextView(context)
    private val promptStore = PromptConfigurationStore(context)
    private var profiles = promptStore.profiles()
    private var currentProfile = profiles.firstOrNull {
        it.id == if (canReplaceSelection) "grammar" else promptStore.selectedProfileId()
    }
        ?: profiles.first { it.id == "grammar" }
    private val modeButtons = mutableMapOf<String, Button>()
    private val modePicker = LinearLayout(context)
    private val writeTab = Button(context)
    private val resultTab = Button(context)
    private val writePanel = LinearLayout(context)
    private val resultPanel = LinearLayout(context)
    private var hasResult = false
    private var activeTab = ContentTab.WRITE
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
        titleBlock.addView(label("ClearWrite AI", 24f, true))
        titleBlock.addView(
            label(
                if (canReplaceSelection) "Fix selected text privately"
                else "Private, offline Qwen writing",
                13f
            ).apply { setTextColor(mutedInk) },
            matchWidth().withTopMargin(2)
        )
        header.addView(titleBlock, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(12)
        })
        if (!canReplaceSelection) {
            val promptSettingsButton = Button(context).apply {
                text = "Prompts"
                isAllCaps = false
                textSize = 12f
                minHeight = dp(42)
                setPadding(dp(8), 0, dp(8), 0)
                background = rounded(Color.WHITE, dp(12), Color.rgb(217, 230, 220), dp(1))
                setTextColor(primaryGreen)
                setOnClickListener {
                    PromptManagerDialog(context, promptStore) { updatedProfiles ->
                        profiles = updatedProfiles.toMutableList()
                        currentProfile = profiles.firstOrNull { it.id == currentProfile.id }
                            ?: profiles.first { it.id == "grammar" }
                        promptStore.saveSelectedProfile(currentProfile.id)
                        updateModeButtons()
                        fixButton.text = actionText(currentProfile)
                    }.show()
                }
            }
            header.addView(promptSettingsButton, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(42)
            ))
        }
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
        if (!canReplaceSelection) {
            modePicker.apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val modeScroll = HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                addView(modePicker, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)))
            }
            writePanel.addView(modeScroll, matchWidth().withTopMargin(9))
            updateModeButtons()
        }
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

        fixButton.text = actionText(currentProfile)
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

        resultHeading.apply {
            text = "Corrected text"
            textSize = 16f
            setTextColor(ink)
            setTypeface(typeface, Typeface.BOLD)
        }
        resultPanel.addView(resultHeading)
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
        val mode = currentProfile

        fixButton.isEnabled = false
        fixButton.text = "Working…"
        fixButton.alpha = 0.75f
        modeButtons.values.forEach { it.isEnabled = false }
        progress.visibility = View.VISIBLE
        showStatus("Preparing the offline writing model…", error = false)
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
                val modelCorrected = localModel.correct(
                    text = text,
                    profile = mode,
                    masterPrompt = promptStore.masterPrompt(),
                    temperature = promptStore.temperature()
                ) { message ->
                    post { showStatus(message, error = false) }
                }
                val corrected = modelCorrected.ifBlank { text }
                val elapsedSeconds = (SystemClock.elapsedRealtime() - startedAt) / 1000.0
                output.text = highlightCorrections(text, corrected)
                resultHeading.text = resultTitle(mode)
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
                showStatus("${resultTitle(mode)}.", error = false)
            } catch (failure: Throwable) {
                // Surface linkage/runtime failures too; the old selection action hid them.
                output.text = ""
                outputCount.text = "Words: 0  ·  Characters: 0  ·  Time: —"
                hasResult = false
                resultTab.isEnabled = false
                showResultTab(false)
                showStatus(
                    "Writing task failed: ${failure.message ?: failure.javaClass.simpleName}",
                    error = true
                )
            } finally {
                fixButton.isEnabled = true
                fixButton.text = actionText(currentProfile)
                fixButton.alpha = 1f
                modeButtons.values.forEach { it.isEnabled = true }
                progress.visibility = View.GONE
            }
        }
    }

    fun close() = localModel.close()

    private fun selectMode(profile: PromptProfile) {
        currentProfile = profile
        promptStore.saveSelectedProfile(profile.id)
        fixButton.text = actionText(profile)
        updateModeButtons()
    }

    private fun updateModeButtons() {
        modePicker.removeAllViews()
        modeButtons.clear()
        profiles.forEach { profile ->
            val button = Button(context).apply {
                text = profile.title
                isAllCaps = false
                textSize = 12f
                minHeight = dp(40)
                minWidth = dp(76)
                setPadding(dp(9), 0, dp(9), 0)
                setOnClickListener { selectMode(profile) }
            }
            modeButtons[profile.id] = button
            modePicker.addView(button, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(42)
            ).apply {
                if (modePicker.childCount > 0) marginStart = dp(5)
            })
            val selected = profile.id == currentProfile.id
            button.background = rounded(
                if (selected) primaryGreen else Color.WHITE,
                dp(11),
                if (selected) primaryGreen else Color.rgb(224, 234, 227),
                dp(1)
            )
            button.setTextColor(if (selected) Color.WHITE else mutedInk)
            button.setTypeface(button.typeface, if (selected) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    private fun showResultTab(showResult: Boolean) {
        val show = showResult && hasResult
        activeTab = if (show) ContentTab.RESULT else ContentTab.WRITE
        writePanel.visibility = if (show) View.GONE else View.VISIBLE
        resultPanel.visibility = if (show) View.VISIBLE else View.GONE
        styleTab(writeTab, selected = activeTab == ContentTab.WRITE)
        styleTab(resultTab, selected = activeTab == ContentTab.RESULT)
        if (show) post { scrollTo(0, 0) }
    }

    private fun actionText(profile: PromptProfile): String = when (profile.id) {
        "grammar" -> "Fix grammar"
        "rephrase" -> "Rephrase"
        "shorter" -> "Make shorter"
        "longer" -> "Make longer"
        else -> "Run ${profile.title}"
    }

    private fun resultTitle(profile: PromptProfile): String =
        if (profile.id == "grammar") "Corrected text" else "${profile.title} result"

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

    private enum class ContentTab { WRITE, RESULT }
}
