# Offline Grammar Fix

An Android text action for Gboard and other keyboards. Select text in an app, choose **Fix grammar**, and Android returns the corrected selection to the app. English checking uses the bundled LanguageTool rules locally; the app has no Internet permission.

## Build

Open this project in Android Studio, or run `./gradlew assembleDebug` after installing Android SDK platform 35 and build tools.

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Use

Install the APK, select editable English text, and choose **Fix grammar** in the selection toolbar (or its overflow menu). The launcher screen has a selectable sample sentence.

Some host apps do not support text replacement or expose the selected text as read-only.

## License

Application source: MIT. Grammar checking uses LanguageTool 6.8, licensed under LGPL-2.1; see https://languagetool.org/ and https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html. LanguageTool's bundled language resources may carry separate notices.
