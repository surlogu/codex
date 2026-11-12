# Offline Grammar Fix

An Android text action for Gboard and other keyboards, plus a standalone editor. Type/paste text or select text in another app and choose **Fix grammar**. The app shows the result, word and character counts, and correction time; you can copy it or return it to the selected field when supported.

The main corrector is a bundled, quantized T5-small model fine-tuned for English grammar correction. It runs locally with Transformers.js and ONNX Runtime WebAssembly. LanguageTool's bundled English rules help keep short spelling fixes from being rewritten awkwardly. The app does not request Internet permission, so the model works offline after installation.

## Build

Open this project in Android Studio, or run `./gradlew assembleDebug` after installing Android SDK platform 35 and build tools.

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. The checked-in APK is at `output/OfflineGrammarFix.apk`.

## Use

Install the APK and either enter text in the app and tap **Fix Grammar**, or select editable English text in another app and choose **Fix grammar** in the selection toolbar (or its overflow menu). The selection action opens the app with the selected text filled in; after correction, choose **Replace selected text** or **Copy corrected text**.

Some host apps do not support text replacement or expose the selected text as read-only.

## License

Application source: MIT. The T5 grammar model is MIT licensed; model attribution is in `app/src/main/assets/models/grammar-t5-small/MODEL_NOTICE.txt`. Transformers.js is Apache-2.0. ONNX Runtime Web is MIT. The LanguageTool fallback is LGPL-2.1; its bundled language resources may carry separate notices. See https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html.
