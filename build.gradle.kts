plugins {
    id("java")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("net.portswigger.burp.extensions:montoya-api:2025.8")
    implementation("net.sf.py4j:py4j:0.10.9.9")
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "21"
    targetCompatibility = "21"
    options.encoding = "UTF-8"
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().filter { it.isDirectory })
    from(configurations.runtimeClasspath.get().filterNot { it.isDirectory }.map { zipTree(it) })
}