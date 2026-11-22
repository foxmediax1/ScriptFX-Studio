plugins {
    id("buildsrc.convention.kotlin-jvm")
    // application  — убрать: конфликтует с compose.desktop (оба делают task "run")
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    implementation(project(":utils"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(libs.kotlinxSerialization)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
}

compose.desktop {
    application {
        mainClass = "net.foxmediax.app.AppKt"
        nativeDistributions {
            packageName = "ScriptFX Studio"
            packageVersion = "0.1.0"
            description = "ScriptFX Story Engine Studio"
            vendor = "FoxMediaX"
        }
    }
}