package com.localgrammar.fix

import android.graphics.Color
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.graphics.Typeface

private data class TextToken(val start: Int, val end: Int, val value: String)

/** Returns corrected text with inserted or changed words highlighted in green. */
internal fun highlightCorrections(original: String, corrected: String): CharSequence {
    if (corrected.isEmpty() || original == corrected) return corrected

    val tokenPattern = Regex("[\\p{L}\\p{N}]+(?:['’][\\p{L}\\p{N}]+)?|[^\\s\\p{L}\\p{N}]")
    val before = tokenPattern.findAll(original).map { TextToken(it.range.first, it.range.last + 1, it.value) }.toList()
    val after = tokenPattern.findAll(corrected).map { TextToken(it.range.first, it.range.last + 1, it.value) }.toList()
    if (before.isEmpty() || after.isEmpty()) return corrected

    // Keep memory bounded for very long pasted documents.
    if (before.size.toLong() * after.size > 1_000_000L) return corrected

    val lcs = Array(before.size + 1) { IntArray(after.size + 1) }
    for (i in before.indices.reversed()) {
        for (j in after.indices.reversed()) {
            lcs[i][j] = if (before[i].value == after[j].value) {
                lcs[i + 1][j + 1] + 1
            } else {
                maxOf(lcs[i + 1][j], lcs[i][j + 1])
            }
        }
    }

    val unchanged = HashSet<Int>()
    var i = 0
    var j = 0
    while (i < before.size && j < after.size) {
        if (before[i].value == after[j].value) {
            unchanged += j
            i++
            j++
        } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
            i++
        } else {
            j++
        }
    }

    val styled = SpannableString(corrected)
    after.forEachIndexed { index, token ->
        if (index !in unchanged) {
            styled.setSpan(
                BackgroundColorSpan(Color.rgb(218, 246, 226)),
                token.start,
                token.end,
                SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            styled.setSpan(
                ForegroundColorSpan(Color.rgb(20, 112, 62)),
                token.start,
                token.end,
                SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            styled.setSpan(
                StyleSpan(Typeface.BOLD),
                token.start,
                token.end,
                SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }
    return styled
}
