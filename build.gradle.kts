plugins {
    id("com.android.application") version "8.9.2" apply false
    id("com.android.library") version "8.9.2" apply false
    id("org.jetbrains.kotlin.android") version "2.1.21" apply false
//    id("org.sonarqube") version "3.5.0.2730"
}

//sonarqube {
//    properties {
//        property("sonar.projectKey", "JahidHasanCO_BMI-Calculator")
//        property("sonar.organization", "jahidhasanco")
//        property("sonar.host.url", "https://sonarcloud.io")
//    }
//}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory.get().asFile)
}
