plugins {
    java
    application
    id("com.gradleup.shadow") version "8.3.0"
}

group   = "com.qanal"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

val picocliVersion  = "4.7.6"
val nettyVersion    = "4.1.115.Final"
val quicVersion     = "0.0.66.Final"
val jacksonVersion  = "2.18.2"

dependencies {
    // ── CLI framework ──────────────────────────────────────────────────────
    implementation("info.picocli:picocli:$picocliVersion")
    // Generates GraalVM reflect-config.json at compile time
    annotationProcessor("info.picocli:picocli-codegen:$picocliVersion")

    // ── JSON ───────────────────────────────────────────────────────────────
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    // ── Netty QUIC (file sender — mirrors DataPlane) ───────────────────────
    implementation("io.netty:netty-all:$nettyVersion")
    implementation("io.netty.incubator:netty-incubator-codec-native-quic:$quicVersion:linux-x86_64")
    implementation("io.netty.incubator:netty-incubator-codec-native-quic:$quicVersion:linux-aarch_64")
    implementation("io.netty.incubator:netty-incubator-codec-native-quic:$quicVersion:osx-x86_64")
    implementation("io.netty.incubator:netty-incubator-codec-native-quic:$quicVersion:osx-aarch_64")

    // ── Hashing (xxHash64 — same as DataPlane) ─────────────────────────────
    implementation("org.lz4:lz4-java:1.8.0")

    // ── Logging (silent — CLI should not print log noise) ─────────────────
    implementation("org.slf4j:slf4j-nop:2.0.16")
}

application {
    mainClass.set("com.qanal.cli.QanalCli")
}

// ── Picocli annotation processor needs project coords ──────────────────────
tasks.compileJava {
    options.compilerArgs.addAll(listOf(
        "-Aproject=${project.group}/${project.name}"
    ))
}

// ── Fat JAR (recommended distribution method) ─────────────────────────────
tasks.shadowJar {
    archiveBaseName.set("qanal")
    archiveClassifier.set("")
    archiveVersion.set("")
    mergeServiceFiles()
    manifest {
        attributes["Main-Class"] = "com.qanal.cli.QanalCli"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
