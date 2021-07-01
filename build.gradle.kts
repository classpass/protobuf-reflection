import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.Duration
import java.net.URI

import com.google.protobuf.gradle.protoc
import com.google.protobuf.gradle.protobuf

plugins {
    kotlin("jvm") version "1.5.20"
    id("com.google.protobuf") version "0.8.16"
    id("com.github.ben-manes.versions") version "0.39.0"
    id("org.jmailen.kotlinter") version "3.4.5"
    id("org.jetbrains.dokka") version "1.4.32"
    id("io.github.gradle-nexus.publish-plugin") version "1.1.0"
    id("net.researchgate.release") version "2.8.1"
    id("com.github.hierynomus.license") version "0.16.1"
    `maven-publish`
    signing
}

repositories {
    mavenCentral()
}

group = "com.classpass.oss.protobuf.reflection"

val deps: Map<String, String> by extra {
    mapOf(
        "junit" to "5.7.2",
        "protobuf" to "3.17.3",
    )
}

dependencies {
    api("com.google.protobuf", "protobuf-java", deps["protobuf"])

    testImplementation("com.google.protobuf:protobuf-java-util:${deps["protobuf"]}")

    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:${deps["junit"]}")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:${deps["junit"]}")
}

tasks {
    test {
        useJUnitPlatform()
    }

    withType<com.hierynomus.gradle.license.tasks.LicenseCheck> {
        // muffle some, but not all, gradle complaints about not directly depending on this, whose output it uses
        dependsOn(provider { named("generateTestProto") })
    }

    register<Jar>("docJar") {
        from(project.tasks["dokkaHtml"])
        archiveClassifier.set("javadoc")
    }

    // releasing should publish
    afterReleaseBuild {
        dependsOn(provider { project.tasks.named("publishToSonatype") })
    }
}

java {
    withSourcesJar()
}

kotlin {
    explicitApi()
}

license {
    this.ext["year"] = OffsetDateTime.now(ZoneOffset.UTC).year.toString()
    header = rootProject.file("LICENSE-header")
    exclude("**/reflect/test/**")
}

// generated source is only used in tests
sourceSets {
    test {
        java {
            srcDirs("build/generated/source/proto/test/java")
        }
    }
}

protobuf {
    // Configure the protoc executable
    protoc {
        // Download from repositories
        artifact = "com.google.protobuf:protoc:${deps["protobuf"]}"
    }
}

publishing {
    publications {
        register<MavenPublication>("sonatype") {
            from(components["java"])
            artifact(tasks["docJar"])
            // sonatype required pom elements
            pom {
                name.set("${project.group}:${project.name}")
                description.set(name)
                url.set("https://github.com/classpass/protobuf-reflection")
                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.html")
                    }
                }
                developers {
                    developer {
                        id.set("marshallpierce")
                        name.set("Marshall Pierce")
                        email.set("575695+marshallpierce@users.noreply.github.com")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/classpass/protobuf-reflection")
                    developerConnection.set("scm:git:https://github.com/classpass/protobuf-reflection.git")
                    url.set("https://github.com/classpass/protobuf-reflection")
                }
            }
        }
    }

    // A safe throw-away place to publish to:
    // ./gradlew publishSonatypePublicationToLocalDebugRepository -Pversion=foo
    repositories {
        maven {
            name = "localDebug"
            url = URI.create("file:///${project.buildDir}/repos/localDebug")
        }
    }
}

// don't barf for devs without signing set up
if (project.hasProperty("signing.keyId")) {
    signing {
        sign(project.extensions.getByType<PublishingExtension>().publications["sonatype"])
    }
}

nexusPublishing {
    repositories {
        sonatype {
            // sonatypeUsername and sonatypePassword properties are used automatically
            stagingProfileId.set("1f02cf06b7d4cd") // com.classpass.oss
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
        }
    }
    // these are not strictly required. The default timeouts are set to 1 minute. But Sonatype can be really slow.
    // If you get the error "java.net.SocketTimeoutException: timeout", these lines will help.
    connectTimeout.set(Duration.ofMinutes(3))
    clientTimeout.set(Duration.ofMinutes(3))
}

release {
    // work around lack of proper kotlin DSL support
    (getProperty("git") as net.researchgate.release.GitAdapter.GitConfig).apply {
        requireBranch = "main"
    }
}
