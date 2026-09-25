rootProject.name = "refusjon"

pluginManagement {
    val kotlinVersion: String by settings
    val kotlinterVersion: String by settings

    plugins {
        kotlin("jvm") version kotlinVersion
        id("org.jmailen.kotlinter") version kotlinterVersion
    }
}
