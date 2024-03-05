plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.test.voicerecorder"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.test.voicerecorder"

        minSdk = 24
        targetSdk = 34

        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    sourceSets.configureEach {
        jniLibs.srcDirs("jniLibs")
    }

    buildTypes {
        getByName("debug") {
            isDebuggable = true
            isJniDebuggable = true
            isMinifyEnabled = false

            ndk.debugSymbolLevel = "full"
            ndk.jobs = Runtime.getRuntime().availableProcessors()

            externalNativeBuild {
                cmake {
                    val flags = arrayOf(
                        "-w",
                        "-Werror=return-type",
                        "-ferror-limit=0",
                        "-fno-exceptions",

                        "-O2",
                        "-fno-omit-frame-pointer"
                    )
                    arguments(
                        "-DANDROID_STL=c++_shared",
                        "-DANDROID_PLATFORM=android-24", // minSdk
                        "-DCMAKE_BUILD_WITH_INSTALL_RPATH=ON",
                        "-DCMAKE_SKIP_RPATH=ON",
                        "-DCMAKE_C_VISIBILITY_PRESET=hidden",
                        "-DCMAKE_CXX_VISIBILITY_PRESET=hidden",
                        "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,--gc-sections,--icf=safe -Wl,--build-id=sha1",
                        "-DCMAKE_C_FLAGS=-D_LARGEFILE_SOURCE=1 ${flags.joinToString(" ")}",
                        "-DCMAKE_CXX_FLAGS=-std=c++17 ${flags.joinToString(" ")}"
                    )
                }
            }
        }

        getByName("release") {
            ndk.debugSymbolLevel = "full"
            ndk.jobs = Runtime.getRuntime().availableProcessors()

            externalNativeBuild {
                cmake {
                    val flags = listOf(
                        "-w",
                        "-Werror=return-type",
                        "-ferror-limit=0",
                        "-fno-exceptions",

                        "-O3",
                        "-finline-functions"
                    )

                    arguments(
                        "-DANDROID_STL=c++_shared",
                        "-DANDROID_PLATFORM=android-24", // minSdk
                        "-DCMAKE_BUILD_WITH_INSTALL_RPATH=ON",
                        "-DCMAKE_SKIP_RPATH=ON",
                        "-DCMAKE_C_VISIBILITY_PRESET=hidden",
                        "-DCMAKE_CXX_VISIBILITY_PRESET=hidden",
                        "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,--gc-sections,--icf=safe -Wl,--build-id=sha1",
                        "-DCMAKE_C_FLAGS=-D_LARGEFILE_SOURCE=1 ${flags.joinToString(" ")}",
                        "-DCMAKE_CXX_FLAGS=-std=c++17 ${flags.joinToString(" ")}"
                    )
                }
            }
        }
    }
    externalNativeBuild {
        cmake {
            path("jni/CMakeLists.txt")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
}

dependencies {

    implementation("com.github.lincollincol:compose-audiowaveform:1.1.1")

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.1")
    implementation("androidx.activity:activity-ktx:1.8.1")
    implementation(platform("androidx.compose:compose-bom:2023.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.08.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}