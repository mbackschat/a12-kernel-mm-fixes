package buildlogic.mm

import org.gradle.api.tasks.JavaExec
import org.gradle.kotlin.dsl.*
import java.io.File

plugins {
    id("buildlogic.mm.mm-java-conventions")
}

val cfg : TypingsProjectConfig = MmCatalog.typingsProjConfigs(rootProject.projectDir)[project.name]  ?: error("no config found for ${project.name}")
version = getVersionFor(rootProject.projectDir, cfg.modelName, cfg.version)
val label = labelFromVersion(version.toString())
val dmAfterRecreateIncludesPath = file("dmAfterRecreateIncludes/dm.json") // there's currently only one dm file in this folder, so it should be safe to simply use a constant name. alternatively we could use something like  getDocumentModelFileName(cfg.variant, cfg.modelName, cfg.version, label)

val recreateIncludesInDm = tasks.register("recreateIncludesInDm") {
    // Note: it is important that origDmJsonPath is only resolved here (and not outside of the task during evaluation of the project itself),
    // because getDocumentModelPath fails in case the label of the checked-in DM jsons has changed and the labels in the versions properties
    // files are still outdated, because `gradle :updateAndCommitVersions` has not been run yet in the root project.
    // That failure of getDocumentModelPath during project evaluation would prevent the execution of `gradle :updateAndCommitVersions` and thereby the automatic update of the stale versions in the properties files.
    val origDmJsonPath = getDocumentModelPath(rootProject.projectDir, cfg.modelName, cfg.version, label, cfg.variant, cfg.isSpecialBulkVariation)
    inputs.file(origDmJsonPath)
    inputs.property("includeRestoreParams", cfg.externalIncludes.map {ei -> listOf(ei.groupFullName, ei.dmId)}) // groupFullname and dmId influence the result of copyDmAndReplaceGroupsWithUnresolvedInclude
    outputs.file(dmAfterRecreateIncludesPath)
    // assign args for copyDmAndReplaceGroupsWithUnresolvedInclude to local variables, otherwise gradle configuration caching doesn't work because a reference to "project" is implicitly captured in teh doLast closure, or so...
    val localOrigDmJsonPath = origDmJsonPath
    val outPath = dmAfterRecreateIncludesPath
    val extIncludeSpec = cfg.externalIncludes
    doLast {
        copyDmAndReplaceGroupsWithUnresolvedInclude(localOrigDmJsonPath, outPath, extIncludeSpec)
    }
}

val typingsGenerator = configurations.maybeCreate("typingsGenerator")

val generateTypingsTask = tasks.register<JavaExec>("generateTypings") {
    inputs.files(recreateIncludesInDm) // should also create the task dependency
    val genSrcRoot = File(projectDir, "genSrc")
    outputs.dir(genSrcRoot)
    doFirst { genSrcRoot.mkdirs() } // note: only local variables should be passed into doFirst, to avoid implicit references to 'project' which would break configuration caching
    classpath = typingsGenerator
    mainClass = "com.mgmtp.a12.kernel.md.document.typed_accessor_gen.TypedAccessorGenerator"
    args = listOf(
        "--document-model-file", dmAfterRecreateIncludesPath.absolutePath,
        "--package-prefix", cfg.packagePrefix(),
        "--output-dir", genSrcRoot.absolutePath,
        "--annotations-fqns", "com.mgmtp.a12.model.utils.OnlyForUsage",
    ) + if (cfg.externalIncludes.isNotEmpty())
            listOf("--external-models") + cfg.externalIncludes.map { it.externalModelsSpec }
        else listOf()
    inputs.property("argList", args)
}

sourceSets {
    main {
        java {
            srcDir(generateTypingsTask) // will use the dir that is set in generateTypings via "outputs.dir(genSrcRoot)"
        }
    }
}

// apparently we cannot directly access the version catalog in a typesafe way from the conventions plugin, so lets get the versions manually for now:
val tomlFile = File(project.rootDir, "libs.versions.toml")
val currentKernelVersion = readTomlProperty(tomlFile, "currentKernelVersion")
val baseModelUtilsVersion = readTomlProperty(tomlFile, "com-mgmtp-a12-base")
dependencies {
    api("com.mgmtp.a12.kernel:kernel-md-document-v2:$currentKernelVersion") // "api" because the document classes occur in e.g. _unwrap() and _viewOf()
    cfg.externalIncludes.forEach { extInclude ->
        add("api", project(extInclude.depProjPath)) // api because the included (e.g. header) classes occur in signatures
    }
    compileOnly("com.mgmtp.a12.base:base-model-utils:$baseModelUtilsVersion") // for annotations like @Experimental
    // Temporary workaround for Gradle bug https://github.com/gradle/gradle/issues/11625: instead of depending on the
    // kernel-md-facade module directly (which triggers the bug), we depend on a fat jar bundling it and all transitive
    // deps. Restore the direct dependency below and drop the project dependency once the bug is fixed; see
    // facade-fatjar-workaround-gradle-11625/build.gradle.kts for the rationale and the removal + verification steps.
    // typingsGenerator("com.mgmtp.a12.kernel:kernel-md-facade:$currentKernelVersion") // for TypedAccessorGenerator, can use a different kernel version as long as generated typings and the document-v2 runtime classes are compatible
    typingsGenerator(project(mapOf("path" to ":facade-fatjar-workaround-gradle-11625", "configuration" to "facadeFatJarElements")))
}

// tests are shared across all mmtypings subprojects, in the :test-mmtypings subproject
delegateTestsToSharedTestProject(":test-mmtypings")


