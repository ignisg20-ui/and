plugins {
    `java-library`
    id("io.github.goooler.shadow") version "8.1.8"
}

group = "com.shield"
version = "1.0.0"
description = "Production-ready security plugin for Paper/Spigot/Purpur 1.21.x"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
    maven("https://oss.sonatype.org/content/repositories/central/")
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains:annotations:24.1.0")
    // Netty is shipped with the server runtime; we only need it for compilation.
    compileOnly("io.netty:netty-all:4.1.108.Final")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveClassifier.set("")
    minimize()
}

tasks.named("build") {
    dependsOn(tasks.named("shadowJar"))
}

tasks.named("jar") {
    enabled = false
}
