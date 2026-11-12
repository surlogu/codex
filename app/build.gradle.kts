plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val compileLlamaFromSource = providers.gradleProperty("compileLlama").orNull == "true"

android {
    namespace = "com.localgrammar.fix"
    compileSdk = 35
    if (compileLlamaFromSource) {
        ndkVersion = "28.2.13676358"
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = "3.31.6"
            }
        }
    }

    androidResources {
        noCompress += "gguf"
    }

    sourceSets {
        getByName("main") {
            jniLibs.setSrcDirs(
                if (compileLlamaFromSource) emptyList<String>() else listOf("src/main/jniLibs")
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/AL2.0",
            "META-INF/LGPL2.1",
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE",
            "META-INF/LICENSE.txt",
            "META-INF/NOTICE",
            "META-INF/NOTICE.txt"
        )
    }

    defaultConfig {
        applicationId = "com.clearwrite.qwen"
        minSdk = 26
        targetSdk = 35
        versionCode = 11
        versionName = "2.1"
        if (compileLlamaFromSource) {
            ndk {
                abiFilters += "arm64-v8a"
            }
            externalNativeBuild {
                cmake {
                    arguments += listOf(
                        "-DCMAKE_BUILD_TYPE=Release",
                        "-DBUILD_SHARED_LIBS=ON",
                        "-DLLAMA_BUILD_APP=OFF",
                        "-DLLAMA_BUILD_COMMON=ON",
                        "-DLLAMA_BUILD_TESTS=OFF",
                        "-DLLAMA_BUILD_EXAMPLES=OFF",
                        "-DLLAMA_BUILD_TOOLS=OFF",
                        "-DLLAMA_OPENSSL=OFF",
                        "-DGGML_NATIVE=OFF",
                        "-DGGML_OPENMP=OFF",
                        "-DGGML_LLAMAFILE=OFF"
                    )
                }
            }
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
}
