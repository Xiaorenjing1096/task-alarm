plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.appalarm"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.appalarm"
        // API 26 is the floor on purpose: it gives us java.time without
        // core-library desugaring, and notification channels / foreground
        // service behaviour are uniform above it. Nothing older is worth the
        // extra branches in an alarm app.
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // ------------------------------------------------------------------------
    // 可选的 release 正式签名。
    //
    // 通过环境变量提供密钥库（CI 里用仓库 Secrets，本地用自己的 shell）：
    //
    //   APPALARM_KEYSTORE_FILE        .jks 路径
    //   APPALARM_KEYSTORE_PASSWORD
    //   APPALARM_KEY_ALIAS
    //   APPALARM_KEY_PASSWORD
    //
    // 没配时回退到调试密钥 —— 侧载没问题，但**绝不能用来分发**：AGP 会在每台
    // 机器上重新生成调试密钥库，于是每个构建者（以及每一次 CI 运行）产出的 APK
    // 签名都不同，用户永远无法覆盖升级，只能先卸载。
    // ------------------------------------------------------------------------
    signingConfigs {
        val keystorePath = System.getenv("APPALARM_KEYSTORE_FILE")
        if (!keystorePath.isNullOrBlank() && file(keystorePath).exists()) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("APPALARM_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("APPALARM_KEY_ALIAS")
                keyPassword = System.getenv("APPALARM_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // 打开 R8 压缩与资源裁剪 —— 「轻量」是这个应用的目标之一。
            // kotlinx.serialization 的运行时自带 consumer ProGuard 规则，所以
            // @Serializable 的序列化器不会被裁掉，但改完必须实机验证一次。
            optimization {
                enable = true
            }
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    // Print per-test outcomes. The file sandbox can kill the Gradle process
    // before it writes the HTML/XML report, so the console log is often the
    // only reliable evidence that the tests ran and what they reported.
    testLogging {
        events("passed", "failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
