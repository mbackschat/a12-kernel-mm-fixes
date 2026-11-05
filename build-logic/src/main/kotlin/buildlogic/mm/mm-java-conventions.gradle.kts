package buildlogic.mm

plugins {
    id("buildlogic.mm.mm-base")
    `java-library`
    `maven-publish`
}

group = "com.mgmtp.a12.kernel"

java {
    withSourcesJar()
    withJavadocJar()
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.javadoc {
    isFailOnError = false
    options.quiet()
    (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:none", true)
}

tasks.withType<AbstractArchiveTask>().configureEach {
    this.isPreserveFileTimestamps = false
    this.isReproducibleFileOrder = true
    // Use permissions from the file system - we probably want this set to false for reproducibility, but it is only available in gradle 9
    // useFileSystemPermissions()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            // we should not need to customize the artifactId here, but should make sure that the project (short) name
            // already matches the intended artifact id - otherwise, gradle might not fully understand this e.g. for
            // resolution of project dependencies, or maybe even in published gradle module descriptor files,
            // see https://github.com/gradle/gradle/issues/847
            from(components["java"])
        }
    }
}

// only publish after tests pass (if any tests are configured in the subproject) (affects java/maven publications, not the npm publications)
tasks.withType<AbstractPublishToMaven>().configureEach {
    dependsOn(tasks.check)
}
