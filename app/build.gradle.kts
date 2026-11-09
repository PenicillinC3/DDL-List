plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val appVersionCode = 29
val appVersionName = "1.18.0"

android {
    namespace = "com.gammac3.ddlapp"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.gammac3.ddlapp"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        // OCR 的 native 库很大；只保留现代手机使用的 64 位 ARM
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    packaging {
        jniLibs {
            // 压缩 .so，避免 APK 体积翻倍
            useLegacyPackaging = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.mlkit.text.recognition.chinese)
    ksp(libs.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.org.json)
}

// 安装包统一导出到仓库根目录下一层的 dist/，文件名 = 应用名 + 版本号。
// debug 是日常侧载包：DDL清单-1.18.0.apk；release 追加 -release 以免覆盖。
fun registerApkExport(buildType: String) {
    val capitalized = buildType.replaceFirstChar { it.uppercase() }
    val exportTask = tasks.register<Copy>("export${capitalized}Apk") {
        dependsOn("assemble$capitalized")
        from(layout.buildDirectory.dir("outputs/apk/$buildType")) {
            include("app-$buildType*.apk")
        }
        into(rootProject.file("dist"))
        rename {
            if (buildType == "debug") {
                "DDL清单-$appVersionName.apk"
            } else {
                "DDL清单-$appVersionName-$buildType.apk"
            }
        }
    }
    tasks.matching { it.name == "assemble$capitalized" }.configureEach {
        finalizedBy(exportTask)
    }
}

registerApkExport("debug")
registerApkExport("release")
