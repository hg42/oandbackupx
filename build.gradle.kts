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

buildscript {
    repositories {
        mavenCentral()
        google()
    }
    dependencies {
        //classpath("com.android.tools.build:gradle:7.0.0-alpha05")
        classpath("com.android.tools.build:gradle:4.2.1")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${System.getProperty("kotlin")}")
        classpath("androidx.navigation:navigation-safe-args-gradle-plugin:2.3.5")
    }
}

plugins {
    id("com.palantir.git-version") version "0.12.3"
}

allprojects {
    repositories {
        mavenCentral()
        google()
        maven(url = "https://jitpack.io")
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}


//println("============================================== version")

//apply(plugin = "com.palantir.git-version")

//val gitVersion: groovy.lang.Closure<com.palantir.gradle.gitversion> by extra
//val gitVersion:     groovy.lang.Closure<*> by extra
val versionDetails: groovy.lang.Closure<com.palantir.gradle.gitversion.VersionDetails> by extra
//val versionDetails: groovy.lang.Closure<*> by extra

val details = versionDetails()
//println("details:   $details")
//println("  branchName:   ${details.branchName}")

val refTime = java.util.GregorianCalendar(2020,0,1).time!!
val startTime = java.util.Date()
//val startTime    = System.currentTimeMillis()
val seconds = ((startTime.time - refTime.time) / 1000).toLong() // explicitly convert to integer
//println("seconds:   $seconds")
//val hours = (seconds / 60 / 60).toLong()
//println("hours:     $hours")

var buildTime    = java.text.SimpleDateFormat("yyMMddHHmmss").format(startTime)!!
var buildNumber  = java.text.SimpleDateFormat("yyDDDHHmm").format(startTime).substring(1, 7)
var buildLabel   = details.branchName.replace("feature-", "").replace("PR-", "")
var buildVersion = "${buildNumber}-${buildLabel}-${details.commitDistance}-${details.gitHash}"
//                           + (details.isCleanTag ? "" : "-DIRTY")

rootProject.ext.set("buildNumber",  buildNumber)
rootProject.ext.set("buildVersion", buildVersion)

/*
println("""
version palantir:
    commitDistance: ${details.commitDistance}
    lastTag:        ${details.lastTag}
    gitHash:        ${details.gitHash}
    gitHashFull:    ${details.gitHashFull}
    branchName:     ${details.branchName}
    isCleanTag:     ${details.isCleanTag}
""")
*/
println("""
version build:
    startTime:      $startTime
    buildTime:      $buildTime
    buildVersion:   $buildVersion
""")

//println("============================================== version.")
