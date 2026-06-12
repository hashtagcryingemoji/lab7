plugins {
    kotlin("jvm") version "2.0.0"
    kotlin("plugin.serialization") version "2.0.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

var serializationVersion = "0.90.0"
var mockkVersion = "1.13.16"

dependencies {
    implementation(project(":shared"))
    implementation(kotlin("stdlib-jdk8"))
    implementation("io.github.pdvrieze.xmlutil:core:${serializationVersion}")
    implementation("io.github.pdvrieze.xmlutil:serialization:${serializationVersion}")
    implementation("org.apache.logging.log4j:log4j-api-kotlin:1.5.0")
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.0.0")
    implementation("org.apache.logging.log4j:log4j-core:2.25.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation("org.apache.kafka:kafka-clients:3.9.0")
}

application{
    mainClass.set("GatewayServerMainKt")
}

tasks.shadowJar {
    manifest {
        attributes["Main-Class"] = "GatewayServerMainKt"

        archiveBaseName.set("ImopGW")
        archiveClassifier.set("")
        archiveVersion.set("")
    }
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}
