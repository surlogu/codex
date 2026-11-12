package com.localgrammar.fix

import android.content.Context
import android.graphics.Color
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class PromptProfile(
    val id: String,
    val title: String,
    val prompt: String,
    val maxTokens: Int
) {
    val builtIn: Boolean get() = id in BUILT_IN_IDS

    companion object {
        val BUILT_IN_IDS = setOf("grammar", "rephrase", "shorter", "longer")
        val defaults = listOf(
            PromptProfile("grammar", "Grammar", "Correct grammar, spelling, punctuation, and capitalization. Keep the same meaning and tone.", 256),
            PromptProfile("rephrase", "Rephrase", "Rephrase this naturally and clearly. Keep the same meaning, facts, and approximate length.", 256),
            PromptProfile("shorter", "Shorter", "Rewrite this more concisely. Keep every important fact and the original meaning.", 192),
            PromptProfile("longer", "Longer", "Expand this into fluent, complete writing. Do not add facts or assumptions that are not in the input.", 384)
        )
    }
}

class PromptConfigurationStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("prompt_configuration", Context.MODE_PRIVATE)

    fun profiles(): MutableList<PromptProfile> {
        val raw = prefs.getString(KEY_PROFILES, null) ?: return PromptProfile.defaults.toMutableList()
        return runCatching {
            val array = JSONArray(raw)
            val loaded = (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val id = item.optString("id").takeIf(String::isNotBlank) ?: return@mapNotNull null
                val title = item.optString("title").trim().takeIf(String::isNotBlank) ?: return@mapNotNull null
                val prompt = item.optString("prompt").trim().takeIf(String::isNotBlank) ?: return@mapNotNull null
                PromptProfile(id, title, prompt, item.optInt("maxTokens", 256).coerceIn(64, 512))
            }.toMutableList()
            PromptProfile.defaults.forEach { default ->
                if (loaded.none { it.id == default.id }) loaded.add(defaultsIndex(default), default)
            }
            loaded
        }.getOrElse { PromptProfile.defaults.toMutableList() }
    }

    fun saveProfiles(profiles: List<PromptProfile>) {
        val array = JSONArray()
        profiles.forEach { profile ->
            array.put(JSONObject().apply {
                put("id", profile.id)
                put("title", profile.title)
                put("prompt", profile.prompt)
                put("maxTokens", profile.maxTokens.coerceIn(64, 512))
            })
        }
        prefs.edit().putString(KEY_PROFILES, array.toString()).apply()
    }

    fun selectedProfileId(): String = prefs.getString(KEY_SELECTED_PROFILE, "grammar") ?: "grammar"
    fun saveSelectedProfile(id: String) { prefs.edit().putString(KEY_SELECTED_PROFILE, id).apply() }

    fun masterPrompt(): String = prefs.getString(KEY_MASTER_PROMPT, DEFAULT_MASTER_PROMPT) ?: DEFAULT_MASTER_PROMPT
    fun saveMasterPrompt(prompt: String) { prefs.edit().putString(KEY_MASTER_PROMPT, prompt).apply() }

    fun temperature(): Float = prefs.getFloat(KEY_TEMPERATURE, 0.3f).coerceIn(0.1f, 1f)
    fun saveTemperature(value: Float) { prefs.edit().putFloat(KEY_TEMPERATURE, value.coerceIn(0.1f, 1f)).apply() }

    private fun defaultsIndex(profile: PromptProfile) = PromptProfile.defaults.indexOfFirst { it.id == profile.id }.coerceAtLeast(0)

    companion object {
        const val DEFAULT_MASTER_PROMPT = "You are a careful English writing assistant. Follow the selected task. Preserve the user's meaning and supplied facts. Do not invent personal details. Return only the finished text, without an explanation or heading."
        private const val KEY_PROFILES = "profiles_v1"
        private const val KEY_SELECTED_PROFILE = "selected_profile"
        private const val KEY_MASTER_PROMPT = "master_prompt"
        private const val KEY_TEMPERATURE = "temperature"
    }
}

class PromptManagerDialog(
    context: Context,
    private val store: PromptConfigurationStore,
    private val onSaved: (List<PromptProfile>) -> Unit
) {
    private val context = context
    private val profiles = store.profiles()
    private lateinit var listContainer: LinearLayout
    private lateinit var masterPromptInput: EditText
    private lateinit var temperatureLabel: TextView
    private lateinit var temperatureSlider: SeekBar

    fun show() {
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        val modelCard = TextView(context).apply {
            text = "Qwen2.5-0.5B-Instruct · Q4_K_M\nOn-device CPU · 4K context · Offline"
            textSize = 14f
            setTextColor(Color.rgb(26, 69, 53))
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(Color.rgb(230, 241, 233), dp(12))
        }
        body.addView(modelCard, matchWidth())

        body.addView(sectionLabel("Model settings"), matchWidth().top(16))
        temperatureLabel = sectionLabel(temperatureText(store.temperature()))
        body.addView(temperatureLabel, matchWidth().top(8))
        temperatureSlider = SeekBar(context).apply {
            max = 9
            progress = ((store.temperature() * 10).toInt() - 1).coerceIn(0, 9)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    temperatureLabel.text = temperatureText((progress + 1) / 10f)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        body.addView(temperatureSlider, matchWidth())

        body.addView(sectionLabel("Prompt master · applies to every tab"), matchWidth().top(14))
        masterPromptInput = EditText(context).apply {
            setText(store.masterPrompt())
            hint = "Shared instructions for every prompt tab"
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            minLines = 3
            maxLines = 6
            textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(Color.WHITE, dp(10), Color.rgb(215, 225, 218), dp(1))
        }
        body.addView(masterPromptInput, matchWidth().top(6))

        body.addView(sectionLabel("Prompt tabs"), matchWidth().top(14))
        listContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        body.addView(listContainer, matchWidth().top(6))
        val addButton = Button(context).apply {
            text = "+ Add prompt tab"
            isAllCaps = false
            setOnClickListener {
                editProfile(null) { created ->
                    profiles.add(created)
                    rebuildProfileList()
                }
            }
        }
        body.addView(addButton, matchWidth().top(8))

        val scroll = ScrollView(context).apply { addView(body) }
        rebuildProfileList()
        val dialog = AlertDialog.Builder(context)
            .setTitle("Model & prompts")
            .setView(scroll)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val master = masterPromptInput.text.toString().trim()
                if (master.isBlank()) {
                    masterPromptInput.error = "Enter shared instructions, or use the default prompt."
                    return@setOnClickListener
                }
                store.saveMasterPrompt(master)
                store.saveTemperature((temperatureSlider.progress + 1) / 10f)
                store.saveProfiles(profiles)
                onSaved(profiles.toList())
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun rebuildProfileList() {
        listContainer.removeAllViews()
        profiles.forEachIndexed { index, profile ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            val title = TextView(context).apply {
                text = profile.title
                textSize = 15f
                setTextColor(Color.rgb(26, 43, 37))
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            row.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(Button(context).apply {
                text = "Edit"
                isAllCaps = false
                setOnClickListener {
                    editProfile(profile) { updated ->
                        profiles[index] = updated
                        rebuildProfileList()
                    }
                }
            })
            if (!profile.builtIn) {
                row.addView(Button(context).apply {
                    text = "Delete"
                    isAllCaps = false
                    setOnClickListener {
                        profiles.removeAt(index)
                        rebuildProfileList()
                    }
                })
            }
            listContainer.addView(row, matchWidth())
        }
    }

    private fun editProfile(existing: PromptProfile?, onSave: (PromptProfile) -> Unit) {
        val fields = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
        }
        val title = EditText(context).apply {
            hint = "Tab name"
            setText(existing?.title.orEmpty())
            setSingleLine(true)
            textSize = 16f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        val tokenCount = EditText(context).apply {
            hint = "Maximum output tokens (64–512)"
            setText((existing?.maxTokens ?: 256).toString())
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val prompt = EditText(context).apply {
            hint = "Instructions for this tab"
            setText(existing?.prompt.orEmpty())
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            minLines = 4
            maxLines = 8
            textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        fields.addView(sectionLabel("Tab name"), matchWidth())
        fields.addView(title, matchWidth())
        fields.addView(sectionLabel("Output length"), matchWidth().top(8))
        fields.addView(tokenCount, matchWidth())
        fields.addView(sectionLabel("Task prompt"), matchWidth().top(8))
        fields.addView(prompt, matchWidth())
        val editor = AlertDialog.Builder(context)
            .setTitle(if (existing == null) "Add prompt tab" else "Edit ${existing.title}")
            .setView(fields)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()
        editor.setOnShowListener {
            editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = title.text.toString().trim()
                val task = prompt.text.toString().trim()
                val tokens = tokenCount.text.toString().toIntOrNull()
                if (name.isBlank()) { title.error = "Enter a tab name"; return@setOnClickListener }
                if (profiles.any { it.title.equals(name, ignoreCase = true) && it.id != existing?.id }) {
                    title.error = "A tab with this name already exists"; return@setOnClickListener
                }
                if (task.isBlank()) { prompt.error = "Enter a task prompt"; return@setOnClickListener }
                if (tokens == null || tokens !in 64..512) { tokenCount.error = "Choose 64–512 tokens"; return@setOnClickListener }
                onSave(PromptProfile(existing?.id ?: UUID.randomUUID().toString(), name, task, tokens))
                editor.dismiss()
            }
        }
        editor.show()
    }

    private fun sectionLabel(text: String) = TextView(context).apply {
        this.text = text
        textSize = 14f
        setTextColor(Color.rgb(71, 88, 78))
    }

    private fun rounded(fill: Int, radius: Int, stroke: Int? = null, strokeWidth: Int = 0) =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radius).toFloat()
            if (stroke != null && strokeWidth > 0) setStroke(dp(strokeWidth), stroke)
        }

    private fun matchWidth() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun LinearLayout.LayoutParams.top(value: Int) = apply { topMargin = dp(value) }
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
    private fun temperatureText(value: Float) = "Temperature · ${"%.1f".format(java.util.Locale.US, value)}"
}
