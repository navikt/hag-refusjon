plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jmailen.kotlinter")
    application
}

configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "io.netty") {
            useVersion("4.2.18.Final")
            because("Override Ktor's Netty til en nyere versjon")
        }
    }
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
    val bakgrunnsjobbVersion: String by project
    val brregClientVersion: String by project
    val dokarkivClientVersion: String by project
    val hagDomeneInntektsmeldingVersion: String by project
    val exposedVersion: String by project
    val flywayCoreVersion: String by project
    val gcpStorageVersion: String by project
    val hikariVersion: String by project
    val kafkaVersion: String by project
    val ktorVersion: String by project
    val logbackEncoderVersion: String by project
    val logbackVersion: String by project
    val pdlClientVersion: String by project
    val postgresqlVersion: String by project
    val utilsVersion: String by project

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("com.google.cloud:google-cloud-storage:$gcpStorageVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("io.ktor:ktor-client-apache5:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logbackEncoderVersion")
    implementation("no.nav.helsearbeidsgiver:brreg-client:$brregClientVersion")
    implementation("no.nav.helsearbeidsgiver:dokarkiv-client:$dokarkivClientVersion")
    implementation("no.nav.helsearbeidsgiver:domene-inntektsmelding:$hagDomeneInntektsmeldingVersion")
    implementation("no.nav.helsearbeidsgiver:hag-bakgrunnsjobb:$bakgrunnsjobbVersion")
    implementation("no.nav.helsearbeidsgiver:pdl-client:$pdlClientVersion")
    implementation("no.nav.helsearbeidsgiver:utils:$utilsVersion")
    implementation("org.apache.kafka:kafka-clients:$kafkaVersion")
    implementation("org.flywaydb:flyway-core:$flywayCoreVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayCoreVersion")
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-json:$exposedVersion")
    implementation("org.postgresql:postgresql:$postgresqlVersion")

    val kotestVersion: String by project
    val mockkVersion: String by project
    val testContainerVersion: String by project
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("org.testcontainers:postgresql:$testContainerVersion")
    testImplementation(testFixtures("no.nav.helsearbeidsgiver:utils:$utilsVersion"))
}

tasks {
    // `./gradlew build` kjører `assemble`, men lager ikke build/install/ (jar-filer og avhengigheter).
    // Dockerfilen kopierer build/install/*/lib, så installDist må kjøre som en del av assemble.
    assemble {
        dependsOn(installDist)
    }

    withType<Test> {
        useJUnitPlatform()
        environment("PDFGEN_REFUSJON_URL", "http://fake-pdfgen/refusjon")
    }
}
