package com.localgrammar.fix

import org.languagetool.JLanguageTool
import org.languagetool.language.AmericanEnglish

/** All checking runs in this process using LanguageTool's bundled English rules. */
object LocalGrammarCorrector {
    private val lock = Any()
    private val tool by lazy { JLanguageTool(AmericanEnglish()) }

    fun correct(input: String): String = synchronized(lock) {
        if (input.isBlank()) return input
        val matches = tool.check(input)
        val corrected = StringBuilder(input)
        // Apply from the end so earlier match offsets remain valid.
        for (match in matches.asReversed()) {
            val suggestion = match.suggestedReplacements.firstOrNull() ?: continue
            corrected.replace(match.fromPos, match.toPos, suggestion)
        }
        corrected.toString()
    }
}
