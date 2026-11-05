pluginManagement {
    includeBuild("build-logic")
    // repository config is injected via the user's (or jenkins') gradle config
    // repositories {
    //     gradlePluginPortal()
    // }
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("libs.versions.toml"))
        }
    }
}

rootProject.name = "metamodel"

include("test-mmtypings")
include("test-mmvalidators")
include("test-mmdmjsons")
// TEMPORARY: workaround subproject for https://github.com/gradle/gradle/issues/11625 - remove once the bug is fixed
// (see facade-fatjar-workaround-gradle-11625/build.gradle.kts for how/when to remove).
include("facade-fatjar-workaround-gradle-11625")

// the "buildlogic.mm.settings-createparametricsubprojects" settings plugin (from ./build-logic) handles the
// dynamic creation of the deployment subprojects, based on the models that exist in ./documentModelsOs and ./documentModels
plugins {
    id("buildlogic.mm.settings-createparametricsubprojects")
}
