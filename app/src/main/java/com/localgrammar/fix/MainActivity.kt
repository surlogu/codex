package com.localgrammar.fix

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope

class MainActivity : AppCompatActivity() {
    private var correctionScreen: CorrectionScreen? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        correctionScreen = CorrectionScreen(this, lifecycleScope)
        setContentView(correctionScreen)
    }

    override fun onDestroy() {
        correctionScreen?.close()
        correctionScreen = null
        super.onDestroy()
    }
}
