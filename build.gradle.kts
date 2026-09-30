plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
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
    val githubPassword: String by project
    mavenCentral()
    maven {
        setUrl("https://maven.pkg.github.com/navikt/*")
        credentials {
            username = "x-access-token"
            password = githubPassword
        }
    }
}

dependencies {
    val gcpStorageVersion: String by project
    val kafkaVersion: String by project
    val ktorVersion: String by project
    val logbackEncoderVersion: String by project
    val logbackVersion: String by project
    val utilsVersion: String by project

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("com.google.cloud:google-cloud-storage:$gcpStorageVersion")
    implementation("io.ktor:ktor-client-apache5:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logbackEncoderVersion")
    implementation("no.nav.helsearbeidsgiver:utils:$utilsVersion")
    implementation("org.apache.kafka:kafka-clients:$kafkaVersion")

    val kotestVersion: String by project
    val mockkVersion: String by project
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation(testFixtures("no.nav.helsearbeidsgiver:utils:$utilsVersion"))
}

tasks {
    withType<Test> {
        useJUnitPlatform()
        environment("PDFGEN_REFUSJON_URL", "http://fake-pdfgen/refusjon")
    }
}
