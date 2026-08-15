plugins {
    alias(libs.plugins.dema.kotlin)
    alias(libs.plugins.dema.spotless)
    application
}

group = "io.github.denis-markushin"

dependencies {
    implementation(libs.clikt)
    implementation(libs.jackson.yaml)
    implementation(libs.jackson.kotlin)
    implementation(libs.kotlin.logging)
    testImplementation(libs.assertk)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.wiremock)
    testRuntimeOnly(libs.junit.launcher)
}

application {
    applicationName = "puzzler"
    mainClass = "io.github.denismarkushin.puzzler.PuzzlerCliKt"
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.wrapper {
    gradleVersion = libs.versions.gradle.get()
    distributionType = Wrapper.DistributionType.BIN
}
