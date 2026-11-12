plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.localgrammar.fix"
    compileSdk = 35

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
        applicationId = "com.localgrammar.fix"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}

configurations.configureEach {
    // JAXB/JAX-WS utilities are pulled by LanguageTool's desktop-side unit
    // dependencies and collide on Android; they are not used by the English
    // correction path.
    exclude(group = "com.sun.xml.bind", module = "jaxb-core")
    exclude(group = "com.sun.xml.bind", module = "jaxb-impl")
    exclude(group = "org.glassfish.jaxb", module = "jaxb-runtime")
    exclude(group = "javax.xml.bind", module = "jaxb-api")
    exclude(group = "javax.activation", module = "javax.activation-api")
    exclude(group = "com.sun.activation", module = "jakarta.activation")
    exclude(group = "com.sun.istack", module = "istack-commons-runtime")
    exclude(group = "com.intellij", module = "annotations")
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.languagetool:language-en:6.8")
}
