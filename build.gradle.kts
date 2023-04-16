plugins {
    application
    kotlin("jvm") version "1.7.20"
    kotlin("plugin.serialization") version "1.7.20"
}

group = "me.fishhawk"
version = "0.0.1"

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://maven.tryformation.com/releases") {
        content { includeGroup("com.jillesvangurp") }
    }
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
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion")
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

    implementation("org.eclipse.angus:angus-mail:2.0.1")

    implementation("org.jsoup:jsoup:1.15.3")

    implementation("com.jillesvangurp:search-client:2.0.0-RC-13")

    implementation("org.litote.kmongo:kmongo-coroutine-serialization:4.9.0")

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