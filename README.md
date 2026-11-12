# ClearWrite AI (Qwen branch)

This branch builds a separate, offline writing app named **ClearWrite AI**. The compact **ClearWrite** grammar app remains on the `offline-grammar-fix` branch, so both apps can be installed side by side.

## Features

- Run grammar and spelling correction, rephrase, shorten, or expand text with Qwen2.5-0.5B-Instruct.
- Open the grammar correction screen from Android's selected-text action; correction starts automatically and can replace the selected text when the source app allows it.
- See the result in its own tab with changed text highlighted, word/character counts, elapsed time, copy, and replace actions.
- Keep inference on the device. The app has no network permission and does not send text to a service.

## Model and device requirements

The Qwen2.5-0.5B-Instruct Q4_K_M GGUF weights are bundled in the APK (491,400,032 bytes). The first run copies the bundled model into app storage and loads it; the phone needs roughly 1 GB of free space for the APK and extracted model together. Inference uses llama.cpp on the ARM64 CPU. The build targets Android 8.0+ arm64 phones; x86 emulator builds are not included. Speed and output quality depend on the phone. A small model can make mistakes, so review important writing.

Model: [Qwen2.5-0.5B-Instruct-GGUF](https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF), Apache-2.0. llama.cpp source is included under `app/src/main/cpp/llama.cpp` and retains its upstream MIT license.

## Build

Use JDK 17 and Android SDK 35, then run:

```sh
./gradlew assembleDebug
```

APK output: `output/ClearWriteAI-Qwen.apk`.

The default build uses the bundled ARM64 native runtime. To rebuild that runtime from the included llama.cpp sources, install NDK `28.2.13676358` and CMake `3.31.6`, then run `./gradlew -PcompileLlama=true assembleDebug`.
