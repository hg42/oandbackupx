/*
 * OAndBackupX: open-source apps backup and restore app.
 * Copyright (C) 2020  Antonios Hazim
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

val buildNumber = rootProject.ext.get("buildNumber")
val buildVersion = rootProject.ext.get("buildVersion")

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-kapt")
    id("androidx.navigation.safeargs")
}

android {

    signingConfigs {
        create("hg42test") {
            storeFile = file("/z/src/android/sign-zip/hg42.keystore")
            storePassword = "hg42pwd"
            keyAlias = "cert"
            keyPassword = "hg42pwd"
        }
    }
    compileSdkVersion(30)

    defaultConfig {

        applicationId = "com.machiav3lli.backup"
        minSdkVersion(26)
        targetSdkVersion(29)
        versionCode = "60$buildNumber".toInt()
        versionName = "6.0.$buildVersion"
        // Tests
        testApplicationId = "${applicationId}.tests"
        // 1) Make sure to use the AndroidJUnitRunner, or a subclass of it. This requires a dependency on androidx.test:runner, too!
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // 2) Connect JUnit 5 to the runner
        testInstrumentationRunnerArgument("runnerBuilder", "de.mannodermaus.junit5.AndroidJUnit5Builder")
        javaCompileOptions {
            annotationProcessorOptions {
                arguments(
                    mapOf(
                        "room.schemaLocation" to "$projectDir/schemas",
                        "room.incremental" to "true"
                    )
                )
            }
        }

        println("\n---------------------------------------- version $versionCode $versionName\n\n")
    }

    buildTypes {
        named("release") {
            minifyEnabled(false)
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("hg42test")
        }
        named("debug") {
            applicationIdSuffix = ".debug"
            minifyEnabled(false)
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("hg42test")
        }
        applicationVariants.all {
            val variant = this
            variant.outputs.all {
                val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
                //println("--< ${output.outputFileName}")
                output.outputFileName = "oabx-${output.name.replace("-release", "")}-${buildVersion}.apk"
                //println("--> ${output.outputFileName}")
            }
        }
    }
    buildFeatures {
        dataBinding = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    lintOptions {
        isAbortOnError = false
    }
    flavorDimensions("dev")
    productFlavors {
        create("hg42") {
            dimension = "dev"
            applicationIdSuffix = ".hg42"
            versionNameSuffix = "-hg42"
        }
        /*
        create("neo") {
            dimension = "dev"
            applicationIdSuffix = ".neo"
            versionNameSuffix = "-neo"
        }
        */
    }
}



val versions: java.util.Properties = System.getProperties()

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:${versions["kotlin"]}")
    // Libs
    implementation("androidx.room:room-runtime:2.3.0")
    implementation("androidx.room:room-ktx:2.3.0")
    implementation("androidx.work:work-runtime-ktx:2.7.0-alpha03")
    kapt("androidx.room:room-compiler:2.3.0")
    implementation("com.google.code.gson:gson:2.8.6")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.3.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha03")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("org.apache.commons:commons-compress:1.20")
    val commonsio = "2.8.0"
    implementation("commons-io:commons-io:$commonsio")
    val libsu = "3.1.2"
    implementation("com.github.topjohnwu.libsu:core:$libsu")
    implementation("com.github.topjohnwu.libsu:io:$libsu")
    implementation("com.scottyab:rootbeer-lib:0.0.8")
    implementation("com.jakewharton.timber:timber:4.7.1")

    // UI
    implementation("androidx.appcompat:appcompat:1.2.0")
    implementation("androidx.fragment:fragment-ktx:1.3.3")
    implementation("com.google.android.material:material:1.3.0")
    implementation("androidx.preference:preference-ktx:1.1.1")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.navigation:navigation-fragment-ktx:2.3.5")
    implementation("androidx.navigation:navigation-ui-ktx:2.3.5")
    val fastadapter = "5.4.1"
    implementation("com.mikepenz:fastadapter:$fastadapter")
    implementation("com.mikepenz:fastadapter-extensions-diff:$fastadapter")
    implementation("com.mikepenz:fastadapter-extensions-binding:$fastadapter")

    // Tests
    implementation("androidx.test:rules:1.3.0")
    androidTestImplementation("androidx.test:runner:1.3.0")
    val junitJupiter = "5.7.1"
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiter")
    androidTestImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiter")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    // (Optional) If "Parameterized Tests" are needed
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiter")
}

// tells all test tasks to use Gradle's built-in JUnit 5 support
tasks.withType<Test> {
    useJUnitPlatform()

    // tells the test runner to display results of all tests,
    // not just failed ones
    testLogging {
        events("passed", "skipped", "failed")
    }
}

