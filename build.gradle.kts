plugins {
    kotlin("jvm")
    id("org.jmailen.kotlinter")
    application
}

kotlin {
    jvmToolchain(25)
}

application {
    mainClass = "no.nav.helsearbeidsgiver.AppKt"
}

group = "no.nav.helsearbeidsgiver"

version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    val ktorVersion: String by project
    val logbackEncoderVersion: String by project
    val logbackVersion: String by project

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logbackEncoderVersion")

    val kotestVersion: String by project
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
}

tasks {
    withType<Test> {
        useJUnitPlatform()
    }
}
