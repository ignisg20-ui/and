plugins {
    `java-library`
    id("io.github.goooler.shadow") version "8.1.8"
}

group = "com.aris"
version = "1.0.0"
description = "Authorization plugin for Paper/Spigot/Purpur 1.21.x"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains:annotations:24.1.0")

    // bcrypt for password hashing (shaded into the final jar).
    implementation("org.mindrot:jbcrypt:0.4")
    // SQLite driver for the default user store; YAML store stays bundled-free.
    implementation("org.xerial:sqlite-jdbc:3.46.1.3")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.named("build") {
    dependsOn("shadowJar")
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveClassifier.set("")
    archiveBaseName.set("arisauth")
    // Relocate jBCrypt so we never clash with other plugins that ship it.
    // We deliberately do NOT relocate org.sqlite: rewriting the package would
    // break the META-INF/services/java.sql.Driver entry consumed by JDBC's
    // ServiceLoader-based driver discovery.
    relocate("org.mindrot.jbcrypt", "com.aris.auth.shaded.jbcrypt")
    mergeServiceFiles()
}

tasks.named<Jar>("jar") {
    enabled = false
}
