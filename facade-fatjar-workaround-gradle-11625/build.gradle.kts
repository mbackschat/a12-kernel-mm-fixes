// VIBECODE DISCLAIMER: this temporary workaround code has been manually reviewed, but with less rigor than I'd use for
// permanent code. I did carefully read (and manually fixup) the explanatory comment though.
// =====================================================================================================================
// Temporary workaround for Gradle bug https://github.com/gradle/gradle/issues/11625 ;
// (a fix was apparently prioritized by the Gradle team around 2026-06-26).
//
// Why: the `typingsGenerator` configuration in build-logic/.../mm-typings-conventions.gradle.kts pulls in
// `kernel-md-facade` to *run* the TypedAccessorGenerator. `kernel-md-facade` transitively depends on older,
// published `com.mgmtp.a12.kernel:mmtypings-*` artifacts. The bug rewrites such a transitive *module* dependency to the
// local *project* publishing the same `group:name`, even inside a configuration (here: `typingsGenerator`) that has
// nothing to do with that project's own output. (Normal gradle options to customize and prevent project subsitutions
// do not successfully prevent this substitution either). So `:mmtypings:A`'s `typingsGenerator` ends up "depending on" project
// `:mmtypings:A` itself; combined with `:mmtypings:A` legitimately consuming its `typingsGenerator` output, that forms
// an apparent cycle. Note it is specifically this rewiring-of-an-unrelated-configuration, not a naive
// "A -> facade -> A(old)" chain.
// While gradle curiously does not directly fail with a detected cycle, this does occasionally crash the build e.g. when
// the transitive dependency to model-header typings is then detected by gradle as an undeclared task-dependency, triggering
// a fail-safe crash.
//
// Fix: we bundle `kernel-md-facade` + all its transitive deps into one fat jar and let `typingsGenerator` depend on
// that jar instead of on the `kernel-md-facade` module. The essential effect is that the dependency graph Gradle
// computes for `typingsGenerator` now ends at the (opaque, coordinate-less) fat jar and never reaches any `mmtypings-*`
// coordinate that could be mis-substituted. Building the fat jar itself is safe too: this subproject has its own,
// non-`mmtypings-*` coordinate, so resolving `kernel-md-facade` here triggers no substitution (the bug only rewrites
// the coordinate equal to the *owning* project of the resolved configuration - confirmed via `dependencyInsight`).
//
// Removing this workaround once gradle#11625 is fixed:
//   1. Verify the fix with the target Gradle version. Pick any typings project that `kernel-md-facade` transitively
//      depends on an (older) version of - e.g. mmtypings-mm_structuralmappingmodel-1. "Picking" just means passing that
//      project path to Gradle (these projects are generated dynamically; there is no folder to cd into), then inspect
//      the graph after temporarily restoring the direct facade dependency (see step 2):
//        gradle :mmtypings:mmtypings-mm_structuralmappingmodel-1:dependencies --configuration typingsGenerator | grep mmtypings-mm_structuralmappingmodel-1
//      With the bug still present the module is rewritten to a project (this must no longer happen):
//        +--- com.mgmtp.a12.kernel:mmtypings-mm_structuralmappingmodel-1:4.6.551350 -> project :mmtypings:mmtypings-mm_structuralmappingmodel-1
//      Once fixed it must stay a plain module:
//        +--- com.mgmtp.a12.kernel:mmtypings-mm_structuralmappingmodel-1:4.6.551350
//   2. In mm-typings-conventions.gradle.kts restore the direct `typingsGenerator("...:kernel-md-facade:...")` line and
//      drop the project dependency on this workaround (both are marked there).
//   3. Delete this folder, its `include(...)` in settings.gradle.kts, and gradle-issue-github-11625.md.
// =====================================================================================================================

import org.gradle.api.file.ArchiveOperations
import org.gradle.kotlin.dsl.support.serviceOf

plugins {
    base
}

val facadeClasspath: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    // only used to *run* the generator tool, so a single kernel version is enough (see mm-typings-conventions.gradle.kts)
    facadeClasspath(libs.kernel.md.facade)
}

val fatJar = tasks.register<Jar>("fatJar") {
    description = "Bundles kernel-md-facade and all its transitive dependencies into one jar (workaround for gradle#11625)."
    archiveBaseName.set("kernel-md-facade-fatjar-gradle11625-workaround")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    // drop jar signatures / per-jar manifests / module descriptors that become invalid or clash once merged
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/MANIFEST.MF", "module-info.class")
    // a plain `zipTree(...)` in a build script captures the script and breaks the configuration cache, so use the
    // injected ArchiveOperations service and capture only the (CC-serializable) provider, not the Configuration itself.
    val archiveOps = serviceOf<ArchiveOperations>()
    val cpElements = facadeClasspath.elements
    from(cpElements.map { locs -> locs.map { loc -> loc.asFile.let { f -> if (f.isDirectory) f else archiveOps.zipTree(f) } } })
}

// consumable configuration that exposes ONLY the fat jar artifact (and NO module dependencies), so the consuming
// typings projects never see any `com.mgmtp.a12.kernel:mmtypings-*` coordinate that Gradle could wrongly substitute.
val facadeFatJarElements: Configuration by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}

artifacts {
    add(facadeFatJarElements.name, fatJar)
}
