package com.localgrammar.fix

import android.content.Context
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.arm.aichat.isModelLoaded
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.FileOutputStream

/** Runs Qwen2.5-0.5B-Instruct locally through llama.cpp. */
class LocalOnDeviceModel(context: Context) {
    private val appContext = context.applicationContext
    private val engine by lazy { AiChat.getInferenceEngine(appContext) }
    private val inferenceLock = Mutex()

    suspend fun correct(text: String, mode: WritingMode, onProgress: (String) -> Unit): String =
        inferenceLock.withLock {
            val modelFile = ensureModelFile(onProgress)
            awaitEngineReady()
            if (!engine.state.value.isModelLoaded) {
                onProgress("Loading Qwen on this device…")
                engine.loadModel(modelFile.absolutePath)
            }

            onProgress("Preparing the writing task…")
            engine.setSystemPrompt(
                "You are a precise English writing assistant for everyday messages and emails. " +
                    "Follow the user's requested editing task. Preserve the original meaning, facts, " +
                    "names, and intent. Do not answer the text or invent details. Return only the " +
                    "finished text, with no explanation, heading, quotation marks, or markdown. " +
                    "If the input is already good, keep it nearly unchanged."
            )

            val instruction = when (mode) {
                WritingMode.GRAMMAR -> "Correct grammar, spelling, punctuation, and capitalization. Keep the same meaning and tone."
                WritingMode.REPHRASE -> "Rephrase this naturally and clearly. Keep the same meaning, facts, and approximate length."
                WritingMode.SHORTER -> "Rewrite this more concisely. Keep every important fact and the original meaning."
                WritingMode.LONGER -> "Expand this into fluent, complete writing. Do not add facts or assumptions that are not in the input."
            }
            val prompt = "$instruction\n\nText to edit:\n<<<\n$text\n>>>\n\nReturn only the edited text."
            val answer = StringBuilder()
            onProgress("${mode.progressLabel} with Qwen…")
            withTimeout(180_000) {
                engine.sendUserPrompt(prompt, if (mode == WritingMode.LONGER) 384 else 256)
                    .collect { token -> answer.append(token) }
            }
            answer.toString().trim().removeSurrounding("\"", "\"").ifBlank { text }
        }

    private suspend fun awaitEngineReady() {
        val current = engine.state.value
        if (current is InferenceEngine.State.Uninitialized || current is InferenceEngine.State.Initializing) {
            withTimeout(60_000) {
                engine.state.first {
                    it is InferenceEngine.State.Initialized || it is InferenceEngine.State.Error
                }
            }
        }
        if (engine.state.value is InferenceEngine.State.Error) engine.cleanUp()
    }

    private suspend fun ensureModelFile(onProgress: (String) -> Unit): File = withContext(Dispatchers.IO) {
        val finalFile = File(appContext.filesDir, "qwen2.5-0.5b-instruct-q4_k_m.gguf")
        if (finalFile.isFile && finalFile.length() == EXPECTED_MODEL_BYTES) return@withContext finalFile
        finalFile.delete()
        val partialFile = File(appContext.filesDir, "qwen-model.partial")
        partialFile.delete()
        appContext.assets.open("models/qwen2.5-0.5b/qwen2.5-0.5b-instruct-q4_k_m.gguf").use { input ->
            FileOutputStream(partialFile).use { output ->
                val buffer = ByteArray(1024 * 1024)
                var total = 0L
                var lastPercent = -1
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    total += count
                    val percent = (total * 100 / EXPECTED_MODEL_BYTES).toInt()
                    if (percent / 10 != lastPercent / 10) {
                        onProgress("Preparing Qwen model… $percent%")
                        lastPercent = percent
                    }
                }
                output.fd.sync()
            }
        }
        check(partialFile.length() == EXPECTED_MODEL_BYTES) { "The bundled Qwen model is incomplete." }
        check(partialFile.renameTo(finalFile)) { "Could not prepare the Qwen model." }
        finalFile
    }

    /** The process-scoped llama.cpp engine stays warm between activities and edits. */
    fun close() = Unit

    private companion object {
        const val EXPECTED_MODEL_BYTES = 491_400_032L
    }
}

enum class WritingMode(val actionText: String, val progressLabel: String, val resultLabel: String) {
    GRAMMAR("Fix grammar", "Checking grammar", "Grammar fixed"),
    REPHRASE("Rephrase", "Rephrasing", "Rephrased text"),
    SHORTER("Shorter", "Shortening", "Shorter text"),
    LONGER("Longer", "Expanding", "Expanded text")
}
