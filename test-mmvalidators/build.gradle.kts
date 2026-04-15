plugins {
    `java-library`
}

description = "tests for the validator generation & deployment config from mm-validators-conventions.gradle.kts"

dependencies {
    api(platform(libs.kernelBom)) // kernel bom, in order to constrain the runtime-api version

    // cover a DV that occurs in modelVersionsThatCanHavePublicArtifacts:
    testImplementation(project(":mmvalidators:mmvalidators-extmpinternal-mm_selectionmodel-2-a12"))
    // cover a DV that has a12internal dmjsons for SME — validators must still be extmpinternal:
    testImplementation(project(":mmvalidators:mmvalidators-extmpinternal-mm_mappingmodel-2-a12"))
    // cover a DV that does NOT occur there
    // (but, in order for the subproject to exist in the OS build too, the chosen DV must occur
    // both in latestKernelMMVersions.properties and in the documentModelsOs folder)
    testImplementation(project(":mmvalidators:mmvalidators-extmpinternal-mm_smm_fieldtypechecks-1-a12"))

    testImplementation(libs.junitJupiter)
    testImplementation(libs.junitJupiterApi)
    testImplementation(libs.junitJupiterParams)
    testRuntimeOnly(libs.junitPlatformLauncher)
}

tasks.test {
    useJUnitPlatform()
}
