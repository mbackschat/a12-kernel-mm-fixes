plugins {
    `java-library`
}

description = "tests for the typedaccessor generation & deployment config from mmtypings-dynamicsubprojetcs.gradle"

dependencies {
    testImplementation(project(":mmtypings:mmtypings-mm_mappingmodel-2")) // the public MM typings must not have the variant in their artifact id / project name (variants should not be visible at the api)
    testImplementation(project(":mmtypings:mmtypings-extmpinternal-mm_mappingmodel-2-none")) // internal "none" variant (contains e.g. REPO_DATA, serves as a positive control for the test that ensures that variant-specific groups are not present in the a12 (public) variant
    testImplementation(project(":mmtypings:mmtypings-mm_combinationmodel-1"))
    testImplementation(project(":mmtypings:mmtypings-mm_selectionmodel-2"))
    // only temporarily for the 31-or-later test testIs31OrLaterTypingsGenerator:
    testImplementation(project(":mmtypings:mmtypings-mm_structuralmappingmodel-1"))
    // NOTE: we should _not_ need a separate document V2 dependency. The typedaccessors should have it as an api dep

    testImplementation(libs.junitJupiter)
    testImplementation(libs.junitJupiterApi)
    testImplementation(libs.junitJupiterParams)
    testRuntimeOnly(libs.junitPlatformLauncher)
}

tasks.test {
    useJUnitPlatform()
}