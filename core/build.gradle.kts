plugins {
    java
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.121-stable")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.processResources {
    filesMatching("paper-plugin.yml") {
        expand("version" to project.version)
    }
}


tasks.jar {
    archiveBaseName.set("Modexa")
}
