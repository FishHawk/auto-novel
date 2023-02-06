plugins {
    application
    kotlin("jvm") version "1.7.20"
    kotlin("plugin.serialization") version "1.7.20"
}

group = "me.fishhawk"
version = "0.0.1"

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    val ktorVersion = "2.2.1"
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-resources:$ktorVersion")
    implementation("io.ktor:ktor-server-caching-headers:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")

    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-java:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")

    implementation("org.codehaus.janino:janino:3.1.9")
    implementation("ch.qos.logback:logback-classic:1.4.5")

    val koinVersion = "3.3.0"
    implementation("io.insert-koin:koin-ktor:$koinVersion")
    implementation("io.insert-koin:koin-logger-slf4j:$koinVersion")
    testImplementation("io.insert-koin:koin-test:$koinVersion")

    implementation("org.jsoup:jsoup:1.15.3")

    implementation("org.litote.kmongo:kmongo-coroutine-serialization:4.8.0")

    val kotestVersion = "5.5.4"
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("io.kotest.extensions:kotest-extensions-koin:1.1.0")
    testImplementation("io.kotest:kotest-assertions-ktor:4.4.3")
}

application {
    mainClass.set("ApplicationKt")
}

tasks.test {
    useJUnitPlatform()
}

//var env = "production"
//
//tasks.processResources {
//    filesMatching("*.conf") {
//        when (env) {
//            "development" -> {
//                expand(
//                    "KTOR_ENV" to "dev",
//                    "KTOR_PORT" to "8081",
//                    "KTOR_MODULE" to "build",
//                    "KTOR_AUTORELOAD" to "true"
//                )
//            }
//
//            "production" -> {
//                expand(
//                    "KTOR_ENV" to "production",
//                    "KTOR_PORT" to "80",
//                    "KTOR_MODULE" to "",
//                    "KTOR_AUTORELOAD" to "false"
//                )
//            }
//        }
//    }
//}
//
//val setDev = tasks.register("setDev") {
//    env = "development"
//}
//
//tasks {
//    "run" {
//        dependsOn(setDev)
//    }
//}

//docker-compose -f docker-compose.dev.yml build --progress plain
//docker-compose -f docker-compose.dev.yml up -d
//docker-compose -f docker-compose.prod.yml up -d
//docker run --name mongo -v $(pwd)/data/db:/data/db -p 27017:27017 -d mongo:6.0.3
