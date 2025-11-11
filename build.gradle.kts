import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.serialization") version "2.2.21"
    application
    id("org.graalvm.buildtools.native") version "0.11.1"
}

group = "me.datafox"
version = "1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_24
    targetCompatibility = JavaVersion.VERSION_24
}

kotlin {
    jvmToolchain(25)
    compilerOptions { jvmTarget = JvmTarget.JVM_24 }
}

application {
    mainClass = "me.datafox.dts.MainKt"
}

graalvmNative {
    binaries.named("main") {
        imageName = "dts"
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("net.dv8tion:JDA:6.1.1")
    implementation("com.xenomachina:kotlin-argparser:2.0.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}