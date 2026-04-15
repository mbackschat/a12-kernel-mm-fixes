package buildlogic.mm

import org.gradle.api.tasks.JavaExec
import org.gradle.kotlin.dsl.*
import java.io.File

plugins {
    id("buildlogic.mm.mm-java-conventions")
}

val cfg : ValidatorsProjectConfig = MmCatalog.validatorsProjConfigs(rootProject.projectDir)[project.name] ?: error("no config found for ${project.name}")
version = getVersionFor(rootProject.projectDir, cfg.modelName, cfg.version)
val label = labelFromVersion(version.toString())

val genSrcDir = project.layout.buildDirectory.dir("genSrc/java").get().asFile
val generateValidatorsConfig = configurations.maybeCreate("generateValidatorsConfig")
val generateValidators = tasks.register<JavaExec>("generateValidators") {
    // Note: it is important that origDmJsonPath is only resolved here (and not outside of the task during evaluation of the project itself),
    // because getDocumentModelPath fails in case the label of the checked-in DM jsons has changed and the labels in the versions properties
    // files are still outdated, because `gradle :updateAndCommitVersions` has not been run yet in the root project.
    // That failure of getDocumentModelPath during project evaluation would prevent the execution of `gradle :updateAndCommitVersions` and thereby the automatic update of the stale versions in the properties files.
    val dmJsonPath = getDocumentModelPath(rootProject.projectDir, cfg.modelName, cfg.version, label, cfg.variant, cfg.isSpecialBulkVariation)
    // inputs/outputs for configuration cache friendliness
    inputs.file(dmJsonPath)
    outputs.dir(genSrcDir)
    genSrcDir.let { genSrcDir -> doFirst { genSrcDir.mkdirs() } } // bind to local variable to avoid capturing "project" into the closure, which would break configuration cache

    classpath = generateValidatorsConfig
    mainClass = "com.mgmtp.a12.kernel.md.model.a12internal.services.codegen.cli.VkValidationCodeGeneratorCli"
    val argsLocal = listOf(
        "-l", "JAVA",
        "-p", cfg.packagePrefix(),
        "-vn", cfg.variant,
        "-i", dmJsonPath.absolutePath,
        "-o", genSrcDir.absolutePath,
    )
    args = argsLocal
    inputs.property("argList", argsLocal)
}

sourceSets {
    main {
        java {
            srcDir(generateValidators)
        }
        // No resources; DM JSONs are provided by :mmdmjsons artifacts
    }
}


// apparently we cannot directly access the version catalog in a typesafe way from the conventions plugin, so lets get the versions manually for now:
val tomlFile = File(project.rootDir, "libs.versions.toml")
val kernelVersion = readTomlProperty(tomlFile, "currentKernelVersion")
val kernelRuntimeArtifactId = readTomlProperty(tomlFile, "kernelRuntimeArtifactId")
val kernelRuntimeApiVerison = readTomlProperty(tomlFile, "minKernelCoreRuntimeApiVersion")
dependencies {
    api("com.mgmtp.a12.kernel:kernel-core-runtime-api:$kernelRuntimeApiVerison") // specify _some_ version even though gradle will infer a minimum version via the trantitive api dependency of the core-runtime, because cyclonedx will outherwise not know how to work with the resulting pom
    implementation("com.mgmtp.a12.kernel:kernel-core-runtime-$kernelRuntimeArtifactId:$kernelVersion")

    // CLI for generating validators; allow bootstrapping mode to exclude mm_common-codegen (the same environment
    // variable dynamically disables metamodel-based validation of inputs in the code generator, disabling the need for
    // the mm_common dependency)
    generateValidatorsConfig("com.mgmtp.a12.kernel:kernel-md-model:$kernelVersion") {
        if (System.getenv("A12_KERNEL_BOOTSTRAP_VALIDATORS_DISABLE_MM_VALIDATION")?.toBoolean() == true) {
            exclude(group = "com.mgmtp.a12.kernel", module = "kernel-mm_common-codegen-v2")
        }
    }
}

// tests are shared accross all mmvalidators subprojects -> don't test here, but depend on :test-mmvalidators:test
evaluationDependsOn(":test-mmvalidators")
tasks.matching { it.name.equals("test") }.configureEach {
    dependsOn(":test-mmvalidators:test")
}
