import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

group = "com.inmc.attendance"
version = "1.0.0"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.+")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    compileOnly("me.clip:placeholderapi:2.12.3")
    testImplementation("io.papermc.paper:paper-api:26.1.2.build.+")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    filteringCharset = "UTF-8"
    val props = mapOf(
        "version" to project.version.toString(),
        "apiVersion" to "26.1",
    )
    inputs.properties(props)
    filesMatching("paper-plugin.yml") { expand(props) }
}

tasks.jar {
    archiveBaseName.set("inmc-attendance")
    archiveClassifier.set("dev")
}

tasks.named<ShadowJar>("shadowJar") {
    archiveBaseName.set("inmc-attendance")
    archiveClassifier.set("")
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    exclude("META-INF/maven/**")
    exclude("module-info.class")
    mergeServiceFiles()
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.build {
    dependsOn(tasks.named("shadowJar"))
}

tasks.named<xyz.jpenilla.runpaper.task.RunServer>("runServer") {
    minecraftVersion("26.1.2")
    jvmArgs("-Xmx2G")
}
