repositories { mavenCentral() }

plugins {
    kotlin("jvm") version "1.9.23"
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform { excludeTags("benchmark") }
    testLogging { showStandardStreams = true }
}

tasks.register<Test>("benchmark") {
    useJUnitPlatform { includeTags("benchmark") }
    testLogging { showStandardStreams = true }
}