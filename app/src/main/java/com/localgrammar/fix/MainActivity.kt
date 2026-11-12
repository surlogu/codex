package com.localgrammar.fix

import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val title = TextView(this).apply {
            text = "Offline Grammar Fix"
            textSize = 24f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        val instructions = TextView(this).apply {
            text = "Select text in an app, open the text action menu, and tap “Fix grammar”. " +
                "The correction runs on your phone; this app does not request internet access.\n\n" +
                "If you do not see the action, open the selection menu’s ⋮ overflow. " +
                "Some apps mark selected text read-only or do not support replacement."
            textSize = 16f
            setLineSpacing(8f, 1f)
        }
        val sample = TextView(this).apply {
            text = "Try:  She go to the store yesterday."
            textSize = 18f
            setPadding(20, 20, 20, 20)
            setBackgroundColor(0xFFF1F3F8.toInt())
            setTextIsSelectable(true)
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setPadding(24, 40, 24, 24)
            addView(title, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(instructions, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 20 })
            addView(sample, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 24 })
        })
    }
}
