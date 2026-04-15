plugins {
    `kotlin-dsl`
}

repositories {
    // are any of the below needed, or are repositories fully configured externally?
    // gradlePluginPortal()
    // mavenCentral()
}

dependencies {
    implementation(gradleApi())
    implementation("com.fasterxml.jackson.core:jackson-core:2.17.+")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.+")
}

gradlePlugin {
    plugins {
        create("settingsCreateParametricSubprojects") {
            id = "buildlogic.mm.settings-createparametricsubprojects"
            implementationClass = "buildlogic.mm.SettingsCreateParametricSubprojectsPlugin"
        }
        // precompiled script plugins 'buildlogic.mm.mm-typings-conventions' and 'buildlogic.mm.mm-dmjsons-conventions' are discovered automatically by their filenames
    }
}
