package buildlogic.mm

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import java.io.File
import java.util.Properties
import java.util.Locale
import java.io.FileInputStream

fun copyDmAndReplaceGroupsWithUnresolvedInclude(
    origDmJsonPath: File,
    outJsonPath: File,
    groupsAndIds: List<ExternalInclude>,
) {
    val mapper = ObjectMapper()
    val jsonRoot = mapper.readTree(origDmJsonPath)
    groupsAndIds.forEach { groupAndId ->
        internalReplaceGroupWithUnresolvedInclude(jsonRoot, groupAndId)
    }
    outJsonPath.toPath().parent?.toFile()?.mkdirs()
    mapper.writerWithDefaultPrettyPrinter().writeValue(outJsonPath, jsonRoot)
}

private fun internalReplaceGroupWithUnresolvedInclude(jsonRoot: JsonNode, groupAndId: ExternalInclude) {
    val groupToReplace = findGroupByPath(jsonRoot.path("content").path("modelRoot"), groupAndId.groupFullName)
    val dmIdForInclude = groupAndId.dmId
    if (groupToReplace != null && groupToReplace is ObjectNode) {
        val groupObj = groupToReplace.path("Group") as? ObjectNode ?: return
        groupObj.remove("elements")
        val includeConfig = groupObj.putObject("includeConfig")
        includeConfig.put("reference", dmIdForInclude)
    }
    val header = jsonRoot.path("header") as? ObjectNode ?: return
    val modelRefs: ArrayNode = if (header.has("modelReferences") && header.get("modelReferences").isArray) {
        header.get("modelReferences") as ArrayNode
    } else {
        header.putArray("modelReferences")
    }
    val includeNode = header.objectNode()
    includeNode.put("reference", dmIdForInclude)
    includeNode.put("purpose", "include")
    includeNode.put("modelType", "document")
    modelRefs.add(includeNode)
}

private fun findGroupByPath(modelRoot: JsonNode, path: String): JsonNode? {
    val pathParts = (if (path.startsWith('/')) path.substring(1) else path).split('/')
    var currentLevel: JsonNode = modelRoot
    var isRootLevel = true
    for (groupName in pathParts) {
        val elementsArray = if (isRootLevel) currentLevel.path("rootGroups") else currentLevel.path("Group").path("elements")
        var found: JsonNode? = null
        if (elementsArray.isArray) {
            for (elem in elementsArray) {
                if (elem.path("name").asText() == groupName) {
                    found = elem
                    break
                }
            }
        }
        if (found == null) {
            throw IllegalArgumentException("subgroup at path $path not found in $modelRoot")
        }
        currentLevel = found
        isRootLevel = false
    }
    return currentLevel
}

fun readTomlProperty(tomlFile: File, propertyName: String): String {
    val regex = Regex("^\\s*${propertyName}\\s*=\\s*\"(.*?)\"")
    tomlFile.useLines { lines ->
        for (line in lines) {
            val m = regex.find(line)
            if (m != null) return m.groupValues[1].ifBlank { error("property $propertyName had blank value in $tomlFile, this is most likely an error") }
        }
    }
    error("property $propertyName not found in $tomlFile")
}

fun getDocumentModelFolderName(dataModel: String, modelVersion: String): String {
    return "${dataModel}_${modelVersion}"
}

/**
 * computes the file name under which the DM json can be found inside the dv-specific subfolder of `documentModels/`
 * or `documentModelsOs/` (where it got stored by the exporter). (reproduces behavior of `DocumentModelGenerator#getFileName` except currently the optional internal-descriptions dms)
 */
fun getDocumentModelFileName(
    application: String,
    dataModel: String,
    modelVersion: String,
    label: String,
    isSpecialBulkVariation: Boolean = false,
    isPrevious: Boolean = false,
): String {
    return "${application}-${dataModel}${if (isSpecialBulkVariation) "_bulk" else ""}-${modelVersion}-${label}${if(isPrevious) "-previous" else ""}.json".lowercase()
}

/**
 * for the dmjsons artifacts, compute the file name that should be used for the (non-previous, non-bulk) dm json copy that does not contain the label in its file name
 */
fun dmResourceFileNameNoLabel(application: String, dataModel: String, modelVersion: String, isSpecialBulkVariation: Boolean = false): String {
    return "${application}-${dataModel}${if(isSpecialBulkVariation) "_bulk" else ""}-${modelVersion}.json".lowercase()
}

/**
 * for the dmjsons artifacts, compute the file name that should be used for the (either previous or non-previous, non-bulk) dm json copy that additionally has the ER version in its file name
 */
fun dmResourceFileNameWithExtVersion(
    application: String,
    dataModel: String,
    modelVersion: String,
    label: String,
    extVersion: String,
    isSpecialBulkVariation: Boolean = false,
): String {
    return ("${application}-${dataModel}${if(isSpecialBulkVariation) "_bulk" else ""}-${modelVersion}-${label}-".lowercase()) + "ER-${extVersion}.json"
}

// TODO (later, after integration of the new artifacts is done and settled) get rid of the special case handling (+migrate versions files and any CI / versionupdate code that might also read them)
fun getKeyForVersionsFile(dataModel: String, modelVersion: String): String {
    val mmModel = dataModel
        .replace("MM_", "mm")
        .replace("CT_", "ct")
        .replace("SelectionModel", "mmSelectionModel") // Special treatment for SelectionModel
    val mmVersion = modelVersion.lowercase()
    return "${mmModel}_${mmVersion}"
}

fun getVersionFor(rootDir: File, modelName: String, modelVersion: String): String {
    val versionsKey = getKeyForVersionsFile(modelName, modelVersion)
    return loadVersions(rootDir)[versionsKey] ?: error("no version found for $versionsKey")
}

/** Loads a .properties file and returns its entries with trimmed, unquoted values. */
private fun loadCleanProperties(path: File): Map<String, String> {
    val props = Properties()
    FileInputStream(path).use { props.load(it) }
    return props.entries.associate { (k, v) ->
        k.toString() to v.toString()
            .trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")
    }
}

private val smeVersionKeysCache = mutableMapOf<File, Set<String>>()

/** Returns the set of property keys present in latestSmeMMVersions.properties (cached). */
fun loadSmeVersionKeys(rootDir: File): Set<String> {
    return smeVersionKeysCache.getOrPut(rootDir) {
        loadCleanProperties(File(rootDir, "latestSmeMMVersions.properties")).keys
    }
}

fun loadVersions(rootDir: File): Map<String, String> {
    return loadAndTrimAndMergeProperties(
        listOf(
            File(rootDir, "latestKernelMMVersions.properties"),
            File(rootDir, "latestServerMMVersions.properties"),
            File(rootDir, "latestEditorMMVersions.properties"),
            File(rootDir, "latestSmeMMVersions.properties"),
        ),
        ignoreMissingFiles = true) // the server, editor and sme versions files do not exist in the OS version of the repo, so we must not fail if they do not exist
}

private fun loadAndTrimAndMergeProperties(paths: List<File>, ignoreMissingFiles: Boolean = false): Map<String, String> {
    return paths.fold(emptyMap()) { acc, path ->
        if (ignoreMissingFiles && !path.exists()) acc
        else acc + loadCleanProperties(path)
    }
}

fun labelFromVersion(version: String): String {
    return version.substringBeforeLast('.')
}

fun getPreVersion(extVersion: String): String = (extVersion.toInt() - 1).toString()

private fun uniqueLabelFor(rootDir: File, modelName: String, version: String): Pair<Int, Int> {
    val latest = detectLatestLabel(rootDir, modelName, version)
    val allLabels = labelsForModel(rootDir, modelName, version)
    val older = allLabels.filter { it != latest && (it.first < latest.first || (it.first == latest.first && it.second < latest.second)) }
    if (older.isNotEmpty()) {
        val olderStr = older.sortedWith(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second })
            .joinToString(", ") { "${it.first}.${it.second}" }
        error("Found older labels for ${modelName}-${version}: ${olderStr}")
    }
    return latest
}

private fun detectLatestLabel(rootDir: File, modelName: String, version: String): Pair<Int, Int> {
    val labels = labelsForModel(rootDir, modelName, version)
    if (labels.isEmpty()) {
        error("No DM JSONs with labels found for ${modelName}_${version} in documentModels*/")
    }
    return labels.maxWith(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second })
}

fun dmFileNamePattern(modelName: String, version: String): Regex {
    return Regex(
        // Note: this pattern currently implicitly excludes "_bulk" dms, which seems fine/good in this case, probably?
        "^(?<variant>[^-]+)-${Regex.escape(modelName)}(?<bulk>_bulk)?-${Regex.escape(version)}-(?<labelMajor>[0-9]+)\\.(?<labelMinor>[0-9]+)(?<previous>-previous)?\\.json$",
        setOf(RegexOption.IGNORE_CASE) // in case windows file-insensitiveness messes something up
    )
}


private fun labelsForModel(rootDir: File, modelName: String, version: String): Set<Pair<Int, Int>> {
    val regex = dmFileNamePattern(modelName, version)
    val merged = mergedFilesByModelVersion(rootDir, modelName, version)
    return merged.asSequence()
        .mapNotNull { f ->
            val m = regex.matchEntire(f.name)
            if (m != null) {
                m.groups["labelMajor"]!!.value.toInt() to m.groups["labelMinor"]!!.value.toInt()
            } else {
                println("WARNING: Encountered unexpected file while scanning DM files for ${modelName}-${version}: ${f.name} (ignored)")
                null
            }
        }
        .toSet()
}

/**
 * @param version format: <labelMajor>.<labelMinor>.<old or current extVersion><4-digit>
 */
private fun parseCounterOrZero(version: String?): Int {
    if (version == null) return 0
    // Expect format: <labelMajor>.<labelMinor>.<extVersion><4-digit>
    // Values in properties are quoted; strip optional quotes first
    val parts = version.split('.')
    if (parts.size != 3) error("unexpected old version string: " + version)
    val extAndCounter = parts[2]
    val extVersionLengthInPatchVersion = 2
    val counter = extAndCounter.substring(extVersionLengthInPatchVersion)
    if (counter.length != 4 || counter.any { !it.isDigit() }) error("unexpected 'counter' part of the old version: " + counter + " in old version " + version)
    return counter.toInt()
}

private fun replacePropertyValueInFile(file: File, key: String, newValue: String, noopIfNotExists: Boolean = false): Boolean {
    if(noopIfNotExists && !file.exists()) {
        return false; // don't fail in this case
    }
    val content = file.readText()
    val pattern = Regex("^\\s*${Regex.escape(key)}\\s*=\\s*\\S*\\s*$",
        setOf(RegexOption.MULTILINE))
    var replaced = false
    var newContent = content.replace(pattern) {
        replaced = true
        "${key} = \"${newValue}\""
    }
    if(replaced) {
        file.writeText(newContent)
    }
    return replaced
}

/**
 * Updates latestKernelMMVersions.properties, latestServerMMVersions.properties, latestEditorMMVersions.properties and latestSmeMMVersions.properties for the given model/version.
 * The new version is derived the label of the dm files for model+version, current extVersion (from libs.versions.toml),
 * and a monotonically incrementing  (even across label changes) counter.
 * using format: <labelMajor>.<labelMinor>.<extVersion><4-digit counter>.
 */
fun setFreshMmVersionInPropertiesFiles(rootDir: File, modelName: String, version: String): String {
    val prevVersion = getVersionFor(rootDir, modelName, version)
    val oldCounter = parseCounterOrZero(prevVersion)
    val newCounterStr = String.format(Locale.ROOT, "%04d", oldCounter + 1)
    val currentLabel = uniqueLabelFor(rootDir, modelName, version)
    val extVersion = readTomlProperty(File(rootDir, "libs.versions.toml"), "extVersion")
    val newVersion = "${currentLabel.first}.${currentLabel.second}.${extVersion}${newCounterStr}"

    val key = getKeyForVersionsFile(modelName, version)
    val kernelProps = File(rootDir, "latestKernelMMVersions.properties")
    val serverProps = File(rootDir, "latestServerMMVersions.properties")
    val editorProps = File(rootDir, "latestEditorMMVersions.properties")
    val smeProps = File(rootDir, "latestSmeMMVersions.properties")
    // Write to all files if the key exists; otherwise leave file untouched
    val wroteKernel = replacePropertyValueInFile(kernelProps, key, newVersion)
    val wroteServer = replacePropertyValueInFile(serverProps, key, newVersion, noopIfNotExists = true) // the server versions file does not exist in the OS version of the repo, so we must not fail if it does not exist
    val wroteEditor = replacePropertyValueInFile(editorProps, key, newVersion, noopIfNotExists = true) // the editor versions file does not exist in the OS version of the repo, so we must not fail if it does not exist
    val wroteSme = replacePropertyValueInFile(smeProps, key, newVersion, noopIfNotExists = true) // the sme versions file does not exist in the OS version of the repo, so we must not fail if it does not exist
    if (!wroteKernel && !wroteServer && !wroteEditor && !wroteSme) {
        println("WARNING: Key '${key}' not found in any properties file; no changes written")
    }
    return newVersion
}

fun setFreshMmVersionInPropertiesFilesForAllDVsThatHaveAVersionSet(rootDir: File) {
    discoverConfiguredModelVersions(rootDir)
        .forEach { (modelName, version) ->
            setFreshMmVersionInPropertiesFiles(rootDir, modelName, version)
    }
}

private fun dmBases(rootDir: File): List<File> = listOf(
    File(rootDir, "documentModelsOs"),
    File(rootDir, "documentModels"),
)

fun dmFolders(rootDir: File, modelName: String, version: String): List<File> {
    val folderName = getDocumentModelFolderName(modelName, version)
    return dmBases(rootDir)
        .map { File(it, folderName) }
        .filter { it.exists() && it.isDirectory }
}

/**
 * Returns a merged view of files for the given model+version subdirectories
 * across documentModelsOs (preferred) and documentModels. If the same filename
 * exists in both, the OS version wins.
 */
fun mergedFilesByModelVersion(
    rootDir: File,
    modelName: String,
    version: String,
    filter: (String) -> Boolean = { true }
): Set<File> {
    val result = LinkedHashMap<String, File>()
    for (folder in dmFolders(rootDir, modelName, version)) {
        val files = folder.listFiles()!!.toList()
        for (f in files) {
            if (!f.isFile) continue
            if (!filter(f.name)) continue
            // OS-first precedence: first inserted wins
            if (!result.containsKey(f.name)) {
                result[f.name] = f
            }
        }
    }
    return result.values.toSet()
}

/**
 * computes the path at which the corresponding documentModel json can be found (where it gets stored by the model-updater code).
 * If a matching file exists both below `documentModelsOs` and `documentModels`, the "Os" one is chosen.
 */
fun getDocumentModelPath(
    rootDir: File,
    modelName: String,
    version: String,
    label: String, // we assume that the label (e.g. parsed from the versions file) is consistent with the label of the actually-present DM json files. If the repo is inconsistent in that regard, getDocumentModelPath would currently fail.
    variant: String,
    isSpecialBulkVariation: Boolean = false
): File {
    val expectedFileName = getDocumentModelFileName(
        application = variant,
        dataModel = modelName,
        modelVersion = version,
        label= label,
        isSpecialBulkVariation = isSpecialBulkVariation)
    return mergedFilesByModelVersion(
        rootDir, modelName, version,{
            it.equals(expectedFileName, true) },)
        .firstOrNull() ?: error("no dm file found for rootDir: $rootDir modelName: $modelName version: $version label: $label variant: $variant")
}

/** Returns all "<modelName>_<version>" folder names across both `documentModelsOs` and `documentModels` */
fun modelVersionFolderNames(rootDir: File): List<String> {
    val seen = LinkedHashSet<String>()
    for (base in dmBases(rootDir)) {
        val children = base.listFiles()?.filter { it.isDirectory } ?: emptyList()
        for (dir in children) {
            seen.add(dir.name)
        }
    }
    return seen.toList()
}

/** Finds all modelName, version pairs for which there are corresponding subfolders in the documentModels or documentModelsOs folders */
fun discoverModelVersionsFromDmFolders(rootDir: File): List<Pair<String, String>> {
    return modelVersionFolderNames(rootDir).map { folder ->
        val splitIdx = folder.lastIndexOf('_')
        require(splitIdx > 0 && splitIdx < folder.length - 1) {
            "Folder $folder did not match expected pattern <modelName>_<version>"
        }
        folder.substring(0, splitIdx) to folder.substring(splitIdx + 1)
    }
}

/**
 * Returns (modelName, version) pairs that have DM folders on disk AND a
 * corresponding version key in the versions properties files.
 */
fun discoverConfiguredModelVersions(rootDir: File): List<Pair<String, String>> {
    val versionsKeys = loadVersions(rootDir).keys
    return discoverModelVersionsFromDmFolders(rootDir).filter { (modelName, version) ->
        val has = getKeyForVersionsFile(modelName, version) in versionsKeys
        if (!has) {
            println("WARNING: no version found for modelName=$modelName, version=$version " +
                "in latestKernelMMVersions.properties, latestServerMMVersions.properties, " +
                "latestEditorMMVersions.properties or latestSmeMMVersions.properties, " +
                "skipping this (model, version) even though there is a corresponding folder ")
        }
        has
    }
}

private data class ProcResult(val code: Int, val out: String, val err: String)

private fun run(pwd: File, vararg args: String): ProcResult {
    val pb = ProcessBuilder(*args)
    pb.directory(pwd)
    val proc = pb.start()
    // note: reading the in/err streams without async could maybe cause trouble if outputs are big
    val stdout = proc.inputStream.bufferedReader().readText().removeSuffix("\n").removeSuffix("\r")
    val stderr = proc.errorStream.bufferedReader().readText().removeSuffix("\n").removeSuffix("\r")
    val code = proc.waitFor()
    return ProcResult(code, stdout, stderr)
}

private fun run_check(pwd: File, vararg args: String): ProcResult {
    val procResult = run(pwd, *args)
    if(procResult.code != 0) {
        error("${procResult.out}\n${procResult.err}\nCommand \"${args.toList().joinToString { " " }}\" exited with code ${procResult.code}")
    }
    return procResult
}

fun git_get_branch(rootProjectDir: File): String {
    return run_check(rootProjectDir, "git", "rev-parse", "--abbrev-ref", "HEAD").out.trim()
}

fun git_tryfetch(rootProjectDir: File) {
    git_change_remote_url_to_ssh(rootProjectDir)
    git_set_fetch_config(rootProjectDir)
    val r = run(rootProjectDir, "git", "fetch", "--all")
    if(r.code != 0) {
        println("${r.out}\n${r.err}\nWARNING: git fetch failed with exit code ${r.code}")
    }
}

private fun git_change_remote_url_to_ssh(rootProjectDir: File) {
    val currentUrl = run_check(rootProjectDir, "git", "remote", "get-url", "origin").out.trim()
    // Match HTTPS Bitbucket URLs like https://host/scm/project/repo.git
    val httpsPattern = Regex("""^https?://([^/]+)/scm/(.+)$""")
    val match = httpsPattern.matchEntire(currentUrl)
    if (match != null) {
        val host = match.groupValues[1]
        val path = match.groupValues[2]
        val sshUser = System.getenv("ssh_username") ?: "git"
        val remoteUrl = "ssh://$sshUser@$host:7999/$path"
        run_check(rootProjectDir, "git", "remote", "set-url", "origin", remoteUrl)
        println("Changed remote url from $currentUrl to $remoteUrl")
    } else {
        println("Remote url is not an HTTPS url, skipping conversion: $currentUrl")
    }
}

private fun git_set_fetch_config(rootProjectDir: File) {
    run_check(rootProjectDir, "git", "config", "--replace-all", "remote.origin.fetch", "+refs/heads/*:refs/remotes/origin/*")
}

fun git_tryfetch_and_checkout(rootProjectDir: File, branch: String) {
    git_tryfetch(rootProjectDir)
    run_check(rootProjectDir, "git", "checkout", branch)
}

fun git_hasChanges(rootProjectDir: File): Boolean {
    val res = run_check(rootProjectDir, "git", "status", "--porcelain")
    val hasChanges = res.out.trim().isNotEmpty()
    println("Has changes: ${res.out}")
    return hasChanges
}

fun git_add(rootProjectDir: File, file: String, ignoreFailure: Boolean = false) {
    val res = run(rootProjectDir, "git", "add", file)
    if(res.code == 0) {
        println("Added $file to git with return message: ${res.out}")
    } else {
        if(ignoreFailure) {
            println("Non-zero exit code from git add ${file} - ignored")
        } else {
            error("failed to git add ${file}, stdout was \n${res.out}\nstderr was\n${res.err}\nexit code was\n${res.code}")
        }
    }
}

fun git_commit(rootProjectDir: File, commitMessage: String): Int {
    val res = run(rootProjectDir, "git", "commit", "-m", commitMessage)
    val status = res.code
    if (status == 0) {
        println("Committed changes with return message: ${res.out}")
    } else {
        println("Commit not successful: ${res.err} ${res.out}")
    }
    println("Commit return status $status")
    return status
}

fun git_try_push(rootProjectDir: File, branch: String) {
    git_pull_with_rebase(rootProjectDir, branch)
    git_push(rootProjectDir, branch)
}


fun git_pull_with_rebase(rootProjectDir: File, branch: String) {
    git_change_remote_url_to_ssh(rootProjectDir)
    val res = run(rootProjectDir, "git", "pull", "origin", branch, "--rebase", "--autostash")
    if (res.code == 0) {
        println("Pull with rebase was successful: ${res.out}")
    } else {
        println("Could not pull with rebase: ${res.err}")
    }
}

fun git_push(rootProjectDir: File, branch: String): Int {
    git_change_remote_url_to_ssh(rootProjectDir)
    val res = run(rootProjectDir, "git", "push", "origin", branch)
    val status = res.code
    if (status == 0) {
        println("Pushed changes successfully to origin/$branch: ${res.out}")
    } else {
        println("Push to origin/$branch not successful: ${res.err}")
    }
    return status
}
