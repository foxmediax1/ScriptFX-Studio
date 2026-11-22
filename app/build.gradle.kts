
plugins {
    id("buildsrc.convention.kotlin-jvm")
    // application конфликтует с compose.desktop по задаче run.
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

    testImplementation(kotlin("test"))
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
