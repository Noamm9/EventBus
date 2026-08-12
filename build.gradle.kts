repositories { mavenCentral() }

plugins {
    kotlin("jvm") version "1.9.23"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

dependencies {
    implementation("ch.qos.logback:logback-classic:1.4.11")
}

tasks.jar { enabled = false }
tasks.shadowJar {
    manifest { attributes["Main-Class"] = rootProject.name }
    archiveBaseName.set(rootProject.name)
    archiveClassifier.set("")
    archiveVersion.set("")
}

tasks.build.get().dependsOn(tasks.shadowJar)