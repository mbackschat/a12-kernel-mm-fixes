import org.gradle.api.tasks.Exec

plugins {
    `java-library`
}

description = "tests for dmjsons dynamic subprojects: verify resource entry paths in Maven JARs and npm tgz"

val mmField_a12_projName = "mmdmjsons-a12internal-mm_field-1-a12"
dependencies {
    // CT_MODEL_HEADER-1 (a12): in modelVersionsThatCanHavePublicArtifacts but NOT in SME file
    // → tests that dmjsons don't become public just because the model is in the public set
    testImplementation(project(":mmdmjsons:mmdmjsons-extmpinternal-ct_model_header-1-a12"))
    // MM_Field-1 (a12): in SME file but NOT in modelVersionsThatCanHavePublicArtifacts, a12 variant
    // → tests that a12 variant of SME-only model becomes a12internal
    testImplementation(project(":mmdmjsons:${mmField_a12_projName}"))
    // MM_Field-1 (none): in SME file but "none" variant (not in variantsForSmeDmjsons)
    // → tests that non-a12 variant of SME model stays extmpinternal; also covers "no -previous JSON" for none variant
    testImplementation(project(":mmdmjsons:mmdmjsons-extmpinternal-mm_field-1-none"))

    // Read and inspect npm .tgz archives
    testImplementation(libs.commonsCompress)

    testImplementation(libs.junitJupiter)
    testImplementation(libs.junitJupiterApi)
    testImplementation(libs.junitJupiterParams)
    testRuntimeOnly(libs.junitPlatformLauncher)
}

evaluationDependsOn(":mmdmjsons:${mmField_a12_projName}") // make sure the npm_pack tasks output is already configured

tasks.test {
    useJUnitPlatform()

    // the test of the npm tgz artifact can't rely on the java dependencies, so we declare this here manually here:
    dependsOn(":mmdmjsons:${mmField_a12_projName}:npm_pack")
    val mmFieldA12PackDir = project(":mmdmjsons:${mmField_a12_projName}").tasks.named<Exec>("npm_pack").get().workingDir.absolutePath
    systemProperty("tgzDir.mmFieldA12", mmFieldA12PackDir)
}
