allprojects {
    group = "net.hristrix.modexa"
    version = "3.0.0"

    repositories {
        mavenCentral()
        maven {
            name = "papermc"
            url = uri("https://repo.papermc.io/repository/maven-public/")
        }
    }
}
