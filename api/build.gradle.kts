plugins {
    `java-library`
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.121-stable")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    withSourcesJar()
}

// The API source lives in core so the Core JAR contains the exact same API classes.
sourceSets {
    main {
        java {
            setSrcDirs(listOf("../core/src/main/java"))
            include("net/hristrix/modexa/api/**")
        }
    }
}


tasks.jar {
    archiveBaseName.set("ModexaAPI")
}
