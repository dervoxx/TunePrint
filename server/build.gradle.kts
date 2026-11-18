plugins {
    kotlin("jvm")
    application
}

kotlin {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

application {
    mainClass.set("pl.tuneprint.server.ServerKt")
}

dependencies {
    implementation(project(":shared"))
    implementation("io.ktor:ktor-server-cio:3.3.1")
    implementation("io.ktor:ktor-server-websockets:3.3.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:3.3.1")
    testImplementation("io.ktor:ktor-client-websockets:3.3.1")
}
