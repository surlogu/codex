# Offline Grammar Fix

An Android text action for Gboard and other keyboards, plus a standalone editor. Type/paste text or select text in another app and choose **Fix grammar**. The app shows the result, word and character counts, and correction time; you can copy it or return it to the selected field when supported. English checking uses the bundled LanguageTool rules locally; the app has no Internet permission.

## Build

Open this project in Android Studio, or run `./gradlew assembleDebug` after installing Android SDK platform 35 and build tools.

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. The checked-in APK is at `output/OfflineGrammarFix.apk`.

## Use

Install the APK and either enter text in the app and tap **Fix Grammar**, or select editable English text in another app and choose **Fix grammar** in the selection toolbar (or its overflow menu). The selection action opens the app with the selected text filled in; after correction, choose **Replace selected text** or **Copy corrected text**.

Some host apps do not support text replacement or expose the selected text as read-only.

## License

Application source: MIT. Grammar checking uses LanguageTool 6.8, licensed under LGPL-2.1; see https://languagetool.org/ and https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html. LanguageTool's bundled language resources may carry separate notices.
