package buildlogic.mm

import org.gradle.kotlin.dsl.*
import org.gradle.api.tasks.Exec
import java.io.File

plugins {
    id("buildlogic.mm.mm-java-conventions")
}

val cfg : DmJsonsProjectConfig = MmCatalog.dmjsonsProjConfigs(rootProject.projectDir)[project.name] ?: error("no config found for ${project.name}")
val versionString = getVersionFor(rootProject.projectDir, cfg.modelName, cfg.version)
val label = labelFromVersion(versionString)
project.version = versionString

val extVersion = readTomlProperty(
    File(project.rootDir, "libs.versions.toml"),
    "extVersion")
val preVersion = getPreVersion(extVersion)
val copyDmJsons = tasks.register<Copy>("copyDmJsons") {
    // Declare inputs that are not already implicit in the from(...) parts (which gradle should understand by default?
    inputs.property("extVersion", extVersion)
    inputs.property("preVersion", preVersion)
    into(project.layout.buildDirectory.dir("copied-dm-jsons"))

    val docModel = getDocumentModelFileName(cfg.variant, cfg.modelName, cfg.version, label, isSpecialBulkVariation = cfg.isSpecialBulkVariation)
    val docModelPrev = getDocumentModelFileName(cfg.variant, cfg.modelName, cfg.version, label, isSpecialBulkVariation = cfg.isSpecialBulkVariation, isPrevious = true)

    dmFolders(project.rootDir, cfg.modelName, cfg.version).forEach { modelFolder ->
        // original labeled
        from(modelFolder) { include(docModel) }
        // without-label variant
        from(modelFolder) {
            include(docModel)
            rename(docModel, dmResourceFileNameNoLabel(cfg.variant, cfg.modelName, cfg.version, isSpecialBulkVariation = cfg.isSpecialBulkVariation))
        }
        // extVersion variant
        from(modelFolder) {
            include(docModel)
            rename(docModel, dmResourceFileNameWithExtVersion(cfg.variant, cfg.modelName, cfg.version, label, extVersion = extVersion, isSpecialBulkVariation = cfg.isSpecialBulkVariation))
        }
        // previous + extVersion(previous)
        from(modelFolder) { include(docModelPrev) }
        from(modelFolder) {
            include(docModelPrev)
            rename(docModelPrev, dmResourceFileNameWithExtVersion(cfg.variant, cfg.modelName, cfg.version, label, extVersion = preVersion, isSpecialBulkVariation = cfg.isSpecialBulkVariation))
        }
    }

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE // first entry should win, because dmFolders() already returns matches from documentModelsOs first and from documentModels second

    val resourcePrefix = cfg.visibility.resourcePrefix()
    if (resourcePrefix.isNotEmpty()) {
        eachFile { path = "$resourcePrefix$path" }
    }

    // capture some project attributes to local variables, so no project reference goes into doFirst closure (otherwise the task does not work with gradle's configuration cache)
    val rootDir = project.rootDir
    val cfgLocal = cfg
    // Validate required inputs exist in at least one of the dmFolders
    doFirst {
        val folders = dmFolders(rootDir, cfgLocal.modelName, cfgLocal.version)
        val requiredFiles = listOf(
            docModel,
        ) + if(cfgLocal.variant == "none") listOf() else listOf(docModelPrev) // apparently, the  "previous" model is not generated for the none variant TODO is this just a bug, or was this maybe temporary in the old release
        val missing = requiredFiles.filter { fn ->
            !folders.any { File(it, fn).isFile }
        }
        if (missing.isNotEmpty()) {
            val searched = folders.map { it.absolutePath }.sorted()
            error(
                "Missing DM JSON(s) ${missing.joinToString(", ")} for ${cfgLocal.modelName}_${cfgLocal.version}. " +
                "Searched in: ${searched.joinToString(", ")}"
            )
        }
    }
}

sourceSets {
    main {
        resources {
            srcDir(copyDmJsons)
        }
    }
}

// Prepare npm package from the same DM JSONs used for the Maven publication
val npmDir = project.layout.buildDirectory.dir("npm")
val npmContentDir = npmDir.map { it.dir("pkg") }
val prepareNpmPackage = tasks.register<Copy>("prepareNpmPackage") {
    from(copyDmJsons)
    // copy and filter the template.package.json / package.json
    from(rootProject.layout.projectDirectory.file("npm-config-templates-for-dmjson-artifacts")) {
        include("template.package.json")
        eachFile {
            name = name.replace("template.package.json", "package.json")
        }
        val replacements = mapOf(
            "\$nameNpm$" to project.name.lowercase(),
            "\$buildVersion$" to versionString
        )
        filter { line: String ->
            var updatedLine = line
            replacements.forEach { (old, new) ->
                updatedLine = updatedLine.replace(old, new)
            }
            updatedLine
        }
    }
    inputs.property("fullVersionFromProps", versionString) // used in package.json replacements

    // license files (see also mm-base.gradle conventions for corresponding config for maven artifacts):
    from(rootProject.rootDir) {
        include("LICENSE")
        include("NOTICE")
        include("THIRD_PARTY_NOTICES")
        include("licenses/**")
    }

    into(npmContentDir)
}

// internal repository config for publishing has been moved out of package.json to .npmrc
// NOTE: this will not be present in the open-source repo, but this should be ok (the copy will fail, but npm tasks are not part of the open-source build).
val npmrcSourceFile = rootProject.layout.projectDirectory.file("npm-config-templates-for-dmjson-artifacts/npmrc").asFile

val npm_publish = tasks.register<Exec>("npm_publish") {
    group = "Publishing"
    description = "Publishes the npm artifact with DM JSONs"
    dependsOn(prepareNpmPackage)
    dependsOn(tasks.check) // similar to the setting for maven publish tasks in java-conventions, only publish after tests (if any exist) have passed
    // write the tgz outside the input directory
    workingDir = npmDir.map { it.dir("npm_publish_artifacts_dir") }.get().asFile
    val npmExecutable = if (System.getProperty("os.name").lowercase().contains("windows")) "npm.cmd" else "npm"
    // pass the package directory explicitly so output goes to workingDir
    val npmrcSourceFileLocal = npmrcSourceFile
    doFirst {
        workingDir.mkdirs()
        // .npmrc may not exist in filtered versions of the repo
        if (npmrcSourceFileLocal.exists()) {
            npmrcSourceFileLocal.copyTo(workingDir.resolve(".npmrc"), overwrite = true)
        }
    }
    commandLine(npmExecutable, "publish", npmContentDir.get().asFile.absolutePath)
}

// npm_pack creates a .tgz like the one that would be built and published by npm_publish, for manual local inspection
val npm_pack = tasks.register<Exec>("npm_pack") {
    group = "Build"
    description = "Packs the npm artifact into a .tgz for inspection"
    dependsOn(prepareNpmPackage)
    // write the tgz outside the input directory
    workingDir = npmDir.map { it.dir("npm_pack_artifacts_dir") }.get().asFile
    val npmExecutable = if (System.getProperty("os.name").lowercase().contains("windows")) "npm.cmd" else "npm"
    // pass the package directory explicitly so output goes to workingDir
    val npmrcSourceFileLocal = npmrcSourceFile
    doFirst {
        workingDir.mkdirs()
        // .npmrc may not exist in filtered versions of the repo
        if (npmrcSourceFileLocal.exists()) {
            npmrcSourceFileLocal.copyTo(workingDir.resolve(".npmrc"), overwrite = true)
        }
    }
    commandLine(npmExecutable, "pack", npmContentDir.get().asFile.absolutePath)
}

tasks.matching {
    it is AbstractPublishToMaven // we want all "normal" publish tasks, to avoid a brittle dependency to exactly the name of the auto-configured jenkins-specific publish task
    && !it.name.contains("ToMavenLocal") // not publishToMavenLocal and related tasks like publishMavenJavaPublicationToMavenLocal, those fit better to npm_pack (because npm_publish is hardcoded to the online prod repository as a target
}.configureEach {
    dependsOn(npm_publish)
}

tasks.matching {
    it.name == "build" || it.name == "publishToMavenLocal"
}.configureEach {
    dependsOn(npm_pack)
}

// tests are shared across all mmdmjsons subprojects, in the :test-mmdmjsons subproject
delegateTestsToSharedTestProject(":test-mmdmjsons")
