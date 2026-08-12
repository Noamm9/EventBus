repositories { mavenCentral() }

plugins {
    kotlin("jvm") version "2.4.10"
    `maven-publish`
    jacoco
    id("org.jetbrains.dokka") version "2.2.0"
    id("org.jetbrains.dokka-javadoc") version "2.2.0"
    signing
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
}

group = "org.noamm"
version = "1.0.0"

java {
    withSourcesJar()
}

val javadocJar = tasks.register<Jar>("javadocJar") {
    dependsOn(tasks.dokkaGeneratePublicationJavadoc)
    archiveClassifier.set("javadoc")
    from(tasks.dokkaGeneratePublicationJavadoc.flatMap { it.outputDirectory })
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

publishing {
    publications {
        create<MavenPublication>("eventbus") {
            from(components["java"])
            artifact(javadocJar)
            artifactId = "eventbus"
            pom {
                name.set("eventbus")
                description.set("A lightweight Kotlin event bus with priorities, event cancellation and annotation subscription.")
                url.set("https://github.com/Noamm9/EventBus")
                licenses {
                    license {
                        name.set("CC0-1.0")
                        url.set("https://creativecommons.org/publicdomain/zero/1.0/")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("Noamm9")
                        name.set("Noam")
                        url.set("https://noamm.org")
                    }
                }
                scm {
                    url.set("https://github.com/Noamm9/EventBus")
                    connection.set("scm:git:https://github.com/Noamm9/EventBus.git")
                    developerConnection.set("scm:git:git@github.com:Noamm9/EventBus.git")
                }
            }
        }
    }
}

signing {
    val keyId = providers.gradleProperty("signingInMemoryKeyId").orNull
    val key = providers.gradleProperty("signingInMemoryKey").orNull
    val password = providers.gradleProperty("signingInMemoryKeyPassword").orNull
    if (key != null && password != null) {
        useInMemoryPgpKeys(keyId, key, password)
        sign(publishing.publications["eventbus"])
    }
}

nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
            snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))
        }
    }
}