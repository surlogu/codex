# ClearWrite

An offline Android grammar editor with a **Fix grammar** text-selection action for Gboard and other keyboards. Type or paste text in the app, or select text in another app and choose **Fix grammar**. Corrected words are highlighted in green; the app also shows word and character counts and correction time. Copy the result or return it to the selected field when supported.

The main corrector is a bundled T5-Efficient-MINI model trained for English grammar correction with additional typo noise. Its ONNX weights are quantized; inference runs locally with ONNX Runtime WebAssembly. LanguageTool's bundled English rules provide a second correction signal. Before showing a result, the app checks that the model has not dropped negation or replaced too much of the input's key content. These safeguards reduce meaning changes, but no grammar model can guarantee perfect results for every sentence. The app does not request Internet permission, so correction works offline after installation.

## Build

Open this project in Android Studio, or run `./gradlew assembleDebug` after installing Android SDK platform 35 and build tools.

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. The checked-in APK is at `output/OfflineGrammarFix.apk`.

## Use

Install the APK and either enter text in the app and tap **Fix Grammar**, or select editable English text in another app and choose **Fix grammar** in the selection toolbar (or its overflow menu). The selection action opens the app with the selected text filled in; after correction, choose **Replace selected text** or **Copy corrected text**.

Some host apps do not support text replacement or expose the selected text as read-only.

## License

Application source: MIT. The T5-Efficient-MINI grammar model is MIT licensed; model attribution is in `app/src/main/assets/models/grammar-t5-efficient-mini/MODEL_NOTICE.txt`. Transformers.js is Apache-2.0. ONNX Runtime Web is MIT. The LanguageTool fallback is LGPL-2.1; its bundled language resources may carry separate notices. See https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html.
