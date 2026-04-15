import groovy.json.JsonSlurper

plugins {
    id("buildlogic.mm.mm-base") // makes utils from Utils.kt and MmCatalog in build-logic available here
    // there is no java stuff to build or publish here in the root project, so we don't need the java or maven-publish plugins
}


group = "com.mgmtp.a12.kernel"

tasks.register("updateAndCommitVersions") {
    group = "KERNEL" // set some group so the task gets shown by gradle tasks by default
    dependsOn("clean", "updateMMVersions", "commitMMVersionChanges") // ordering is configured by mustRunAfter in commitMMVersionChanges
}

tasks.register("getApplications") {
    group = "KERNEL"
    doLast {
        println(buildlogic.mm.MmCatalog.variants.joinToString(","))
    }
}


tasks.register("updateMMVersions") {
    if (project.hasProperty("dataModel") && project.hasProperty("modelVersion")) {
        val modelName = project.property("dataModel") as String // assign to local variables, because gradle --configuration-cache does not work if an implicit project reference gets captured in the doLast closure
        val modelVersion = project.property("modelVersion") as String
        val rootDir = project.rootProject.rootDir
        doFirst {
            val newVersion = buildlogic.mm.setFreshMmVersionInPropertiesFiles(rootDir, modelName, modelVersion)
            val key = buildlogic.mm.getKeyForVersionsFile(modelName, modelVersion)
            println("Updated $key to $newVersion")
        }
    } else {
        assert(!project.hasProperty("dataModel") && !project.hasProperty("modelVersion"))
        val rootDir = project.rootProject.rootDir
        doFirst {
            buildlogic.mm.setFreshMmVersionInPropertiesFilesForAllDVsThatHaveAVersionSet(rootDir)
            println("Updated versions for all DVs that have entries in versions files")
        }
    }
}

tasks.register("commitMMVersionChanges") {
    group = "KERNEL"
    description = "Commits the MM version file changes"
    mustRunAfter(tasks.named("updateMMVersions"))
    if (!project.hasProperty("noCommit") || project.property("noCommit") == "false") {
        val rootDir = project.rootProject.rootDir
        val hasDataModel = project.hasProperty("dataModel")
        val dataModel = if (hasDataModel) project.property("dataModel") as String else ""
        val modelVersion = if (hasDataModel) project.property("modelVersion") as String else ""
        val ticketNumber = if (project.hasProperty("ticket")) project.property("ticket") as String else "MM-AUTOMATED"
        doFirst {
            val branch = buildlogic.mm.git_get_branch(rootDir)
            buildlogic.mm.git_tryfetch_and_checkout(rootDir, branch) // NOTE: I'm not sure if fetch & checkout is still right here, but let's not change this right now as part of the current refactoring...
            if (buildlogic.mm.git_hasChanges(rootDir)) {
                buildlogic.mm.git_add(rootDir, "latestServerMMVersions.properties", true) // the server versions file does not exist in the OS version of the repo, so we must not fail if it can't be git-added
                buildlogic.mm.git_add(rootDir, "latestEditorMMVersions.properties", true) // the editor versions file does not exist in the OS version of the repo, so we must not fail if it can't be git-added
                buildlogic.mm.git_add(rootDir, "latestSmeMMVersions.properties", false) // SME is part of a12 (open source), so this file always exists
                buildlogic.mm.git_add(rootDir, "latestKernelMMVersions.properties", false) // the kernel versions file always exists, so we should not need "ignoreFailure"

                val commitMessage = if (!dataModel.isEmpty() && !modelVersion.isEmpty()) {
                    val mmName = "$dataModel-$modelVersion"
                    val newVersion = buildlogic.mm.getVersionFor(rootDir, dataModel, modelVersion)
                    "$ticketNumber: Update $mmName version to $newVersion - from $branch"
                } else {
                    assert(dataModel.isEmpty() && modelVersion.isEmpty())
                    "$ticketNumber: Update all MM versions - from $branch"
                }
                buildlogic.mm.git_commit(rootDir, commitMessage)
            } else {
                println("No changes to commit")
            }
        }
    }
}

tasks.register("gitRebasePush") {
    group = "KERNEL"
    description = "Pulls with rebase, then pushes committed changes to the remote"
    val rootDir = project.rootProject.rootDir
    doLast {
        val branch = buildlogic.mm.git_get_branch(rootDir)
        buildlogic.mm.git_try_push(rootDir, branch)
    }
}

tasks.register("getBuildVersion") {
    group = "KERNEL"
    val rootDir = project.rootProject.rootDir
    val hasDataModel = project.hasProperty("dataModel")
    val dataModel = if (hasDataModel) project.property("dataModel") as String else ""
    val modelVersion = if (hasDataModel && project.hasProperty("modelVersion")) project.property("modelVersion") as String else ""
    doLast {
        if ( dataModel.isEmpty() || modelVersion.isEmpty()) {
            throw GradleException("error - mandatory property missing")
        }
        // returns the version as stored in the properties files - this will only match the freshly deployed version if it is called _after_ the gradle call that updated the version (not within the same gradle build)
        println(buildlogic.mm.getVersionFor(rootDir, dataModel, modelVersion))
    }
}


// migrated: git_* functions now live in Utils.kt

// --------------------------------------------------------------------------------------------------------
// Apply the mm-typings/mm-dmjsons/mm-validators convention plugin to the dynamically generated subprojects
// --------------------------------------------------------------------------------------------------------
project(":mmtypings").subprojects {
    apply(plugin = "buildlogic.mm.mm-typings-conventions")
}

project(":mmdmjsons").subprojects {
    apply(plugin = "buildlogic.mm.mm-dmjsons-conventions")
}

project(":mmvalidators").subprojects {
    apply(plugin = "buildlogic.mm.mm-validators-conventions")
}

// -----------------------------------------------------------------------------------
// aggregate build and publish tasks (per-DV and overall), e.g. for use in jenkinsfile
// -----------------------------------------------------------------------------------
val tasksNamesForAggegation = listOf("publishAllPublicationsToMgm-maven-releasesRepository", "publishToMavenLocal", "build")
tasksNamesForAggegation.forEach { taskName ->
    tasks.register("aggregateAllDVs_$taskName") {
        group = "aggregation"
        // dependsOn will be configured by the dv-specific aggregate tasks below
    }
}

buildlogic.mm.MmCatalog.depProjsPathsByDVForAggregatetasks(rootProject.projectDir).forEach { (dv, subprojpathsForDV) ->
    tasksNamesForAggegation.forEach { taskName ->
        val aggregateTaskForDVName = "aggregate${dv}_$taskName"
        val aggregateTaskForDV = tasks.findByName(aggregateTaskForDVName)?.let { tasks.named(aggregateTaskForDVName) }
            ?: tasks.register(aggregateTaskForDVName) {
                group = "aggregation"
            }
        aggregateTaskForDV.configure {
            subprojpathsForDV.forEach { subProjPath ->
                dependsOn("$subProjPath:$taskName")
            }
        }
        tasks.named("aggregateAllDVs_$taskName").configure {
            dependsOn(aggregateTaskForDV)
        }
    }
}
