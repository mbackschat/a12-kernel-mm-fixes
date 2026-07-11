package buildlogic.mm

import java.io.File

object MmCatalog {
    /**
     * Note: even if a model version occurs here, only some of its artifacts (and probably at most one variant)
     * are public.
     * But no artifacts for model versions that do not occur here should be public.
     */
    val modelVersionsThatCanHavePublicArtifacts = setOf(
        "CT_MODEL_HEADER-1",
        "CT_SELECTION-V1", // transitively used in public MM_SelectionModel-2
        "MM_CombinationModel-1",
        "MM_MappingModel-2",
        "MM_SelectionModel-2",
        "MM_StructuralMappingModel-1",
    )

    /**
     * all known variants (aka Anwendungen for Anwendungseinschränkungen) that exist for our metamodels
     */
    val variants = setOf(
        "repo",
        "codegen",
        "import",
        "gui",
        "a12",
        "a12types",
        "a12_cgen",
        "none",
    )

    val variantsForSmeDmjsons = setOf("a12")

    val variantForPublicTypings = "a12types"

    /**
     * @return a map from subproject name to config for that subproject
     */
    fun dmjsonsProjConfigs(projRootDir: File): Map<String, DmJsonsProjectConfig> {
        return modelVersionVariantsFromFiles(projRootDir)
            .map { makeDmJsonsProjectConfig(it, projRootDir) }
            .groupingBy { it -> it.subprojectName() }
            .reduce { key, firstOrAccumulator, additionalElem -> error("subproject name collision for ${key} " +
                    "already got ${firstOrAccumulator} but encountered additional element ${additionalElem}") }
    }

    /**
     * @return a map from subproject name to config for that subproject
     */
    fun validatorsProjConfigs(projRootDir: File): Map<String, ValidatorsProjectConfig> {
        return modelVersionVariantsFromFiles(projRootDir)
            .map { makeValidatorsProjectConfig(it, projRootDir) }
            .associateBy { it -> it.subprojectName() }
    }

    /**
     * @return a map from subproject name to config for that subproject
     */
    fun typingsProjConfigs(projRootDir: File): Map<String, TypingsProjectConfig> {
        return modelVersionVariantsFromFiles(projRootDir)
            // .filter { !it.isSpecialBulkVariation } we do _not_ exclude the bulk versions, our backend team uses those typings too
            // .filter { it.variant == variantForPublicTypings } no longer filter by this - other variants get typings too, but those typings will be internal
            .map { makeTypingsProjectConfig(it, projRootDir) }
            .associateBy { it -> it.subprojectName() }
    }

    fun depProjsPathsByDVForAggregatetasks(projRootDir: File): Map<String, List<String>> {
        val dmjsonProjPathsByDV = dmjsonsProjConfigs(projRootDir).entries.groupBy(
            keySelector= {"${it.value.modelName}-${it.value.version}"},
            valueTransform= {":mmdmjsons:${it.key}"},
        )
        val validatorProjPathsByDV = validatorsProjConfigs(projRootDir).entries.groupBy(
            keySelector= {"${it.value.modelName}-${it.value.version}"},
            valueTransform= {":mmvalidators:${it.key}"},
        )
        val typingsProjPathsByDV = typingsProjConfigs(projRootDir).entries.groupBy(
            keySelector= {"${it.value.modelName}-${it.value.version}"},
            valueTransform= {":mmtypings:${it.key}"},
        )
        return listOf(dmjsonProjPathsByDV, validatorProjPathsByDV, typingsProjPathsByDV).mergeAll()
    }
}

fun <K, V> Iterable<Map<K, List<V>>>.mergeAll(): Map<K, List<V>> {
    return this.flatMap { it.entries }
        .groupBy({ it.key }, { it.value }) // Erzeugt Map<K, List<List<V>>>
        .mapValues { it.value.flatten() }   // Flacht die verschachtelten Listen ab
}


private fun subProjCfgForInclude(modelVersion: String, variant: String, rootDir: File): TypingsProjectConfig {
    val idx = modelVersion.lastIndexOf("-")
    if(idx <= 0 || idx >= modelVersion.length - 1) {
        error("modelVersion has unexpected format, expected <modelName>-<version>")
    }
    val modelName = modelVersion.substring(0, idx)
    val version = modelVersion.substring(idx + 1)
    return makeTypingsProjectConfig(ModelVersionVariant(modelName = modelName, version = version, variant = variant, isSpecialBulkVariation = false /*include is never the derived "bulk" dm */), rootDir)
}

private fun externalIncludesForHeader(variant: String, rootDir: File) = listOf(
    ExternalInclude(
        dmId = "CT_MODEL_HEADER_1",
        subprojThatProvidesExternalIncludeTypings = subProjCfgForInclude("CT_MODEL_HEADER-1", variant, rootDir),
        rgNameIfSingleRgInclude = "header",
        groupFullName = "/header",
    )
)

/**
 * A map from `"<modelName>-<version>"` to a function `(variant, rootDir) -> externalIncludeSpecs`.
 *
 * e.g. `typingsThatHaveCustomExternalIncludes.get("MM_MappingModel").invoke("a12", rootDir)` returns the external include spec
 * that references the "Header" typings of the a12 variant.
 *
 * Note: the somewhat weird shape of a map with values that are functions is chosen because `subProjCfgForInclude`
 * itself relies on this map, so we need the indirect recursive calls to subProjCfgForInclude to happen lazily
 * and only for the entries that are actually needed; otherwise the recursion will not terminate even if the
 * `subprojThatProvidesExternalIncludeTypings` themselves are not circular.
 */
private val typingsThatHaveCustomExternalIncludes: Map<String, (String, File) -> List<ExternalInclude>> = mapOf(
    "MM_MappingModel-2" to {variant, rootDir -> externalIncludesForHeader(variant, rootDir)},
    "MM_CombinationModel-1" to {variant, rootDir -> externalIncludesForHeader(variant, rootDir)},
    "MM_StructuralMappingModel-1" to {variant, rootDir -> externalIncludesForHeader(variant, rootDir)},
    "SelectionModel-V1" to {variant, rootDir -> listOf(
        ExternalInclude(
            dmId = "CT_SELECTION_V1",
            subprojThatProvidesExternalIncludeTypings = subProjCfgForInclude("CT_SELECTION-V1", variant, rootDir),
            rgNameIfSingleRgInclude = "Selection",
            groupFullName = "/Data",
        ),
        ExternalInclude(
            dmId = "CT_SELECTION_V1",
            subprojThatProvidesExternalIncludeTypings = subProjCfgForInclude("CT_SELECTION-V1", variant, rootDir),
            rgNameIfSingleRgInclude = "Selection",
            groupFullName = "/Validation",
        ),
    )},
    "MM_SelectionModel-2" to {variant, rootDir -> listOf(
        ExternalInclude(
            dmId = "CT_SELECTION_V1",
            subprojThatProvidesExternalIncludeTypings = subProjCfgForInclude("CT_SELECTION-V1", variant, rootDir),
            rgNameIfSingleRgInclude = "Selection",
            groupFullName = "/content/Data",
        ),
        ExternalInclude(
            dmId = "CT_SELECTION_V1",
            subprojThatProvidesExternalIncludeTypings = subProjCfgForInclude("CT_SELECTION-V1", variant, rootDir),
            rgNameIfSingleRgInclude = "Selection",
            groupFullName = "/content/Validation",
        ),
        ExternalInclude(
            dmId = "CT_SELECTION_V1",
            subprojThatProvidesExternalIncludeTypings = subProjCfgForInclude("CT_SELECTION-V1", variant, rootDir),
            rgNameIfSingleRgInclude = "Selection",
            groupFullName = "/content/Computation",
        ),
    ) + externalIncludesForHeader(variant, rootDir)}
)

/**
 * represents a DV + variant, for which a mmdmjsons or mmvalidators subproject is dynamically created and configured
 * (not label - label is fetched from latestKernelMMVersions.properties, latestServerMMVersions.properties, latestEditorMMVersions.properties or latestSmeMMVersions.properties)
 *
 * @property modelName like the "d" in dvl
 * @property version like the "v" in dvl
 * @property variant aka Anwendung (see [MmCatalog.variants] for the values that are normally possible for our metamodels)
 * @property visibility controls the visibility of the classes and resources in the published artifacts for this metamodel
 */
data class ModelVersionVariant(
    val modelName: String,
    val version: String,
    val variant: String,
    val isSpecialBulkVariation: Boolean,
)

/**
 * @property modelName like the "d" in dvl
 * @property version like the "v" in dvl
 * @property variant aka Anwendung (see [MmCatalog.variants] for the values that are normally possible for our metamodels)
 * @property visibility controls the visibility of the classes and resources in the published artifacts for this metamodel
 */
interface BaseProjectConfig {
    val modelName: String
    val version: String
    val variant: String
    val visibility: Visibility
    fun subprojectName(): String
}

data class DmJsonsProjectConfig(
    override val modelName: String,
    override val version: String,
    override val variant: String,
    override val visibility: Visibility,
    val isSpecialBulkVariation: Boolean,
) : BaseProjectConfig {
    override fun subprojectName(): String {
        // we add the "mmdmjsons" prefix in the project name (in addition to the ":mmdmjsons:" part in the project path
        // because of https://github.com/gradle/gradle/issues/847)
        // we put the "internal" part at the front rather than at the back, because otherwise the "a12" variant would read "a12-internal" rather than "internal"
        return "mmdmjsons${visibility.artifactInfix()}-${modelName}-${version}-${variant}${if (isSpecialBulkVariation) "_bulk" else ""}".lowercase()
    }
}
fun makeDmJsonsProjectConfig(mvv: ModelVersionVariant, rootDir: File): DmJsonsProjectConfig {
    return DmJsonsProjectConfig(
        modelName= mvv.modelName,
        version = mvv.version,
        variant = mvv.variant,
        visibility = determineVisibility(mvv, ArtifactType.DMJSONS, rootDir),
        isSpecialBulkVariation = mvv.isSpecialBulkVariation
    )
}

data class ValidatorsProjectConfig(
    override val modelName: String,
    override val version: String,
    override val variant: String,
    override val visibility: Visibility,
    val isSpecialBulkVariation: Boolean
) : BaseProjectConfig {
    override fun subprojectName(): String {
        // we add the "mmvalidators" prefix in the project name (in addition to the ":mmvalidators:" part in the project path
        // because of https://github.com/gradle/gradle/issues/847)
        // we put the "internal" part at the front rather than at the back, because otherwise the "a12" variant would read "a12-internal" rather than "internal"
        return "mmvalidators${visibility.artifactInfix()}-${modelName}-${version}-${variant}${if (isSpecialBulkVariation) "_bulk" else ""}".lowercase()
    }
    fun packagePrefix(): String {
        // modelName, version and variant are already appended by the validation code generator (in some order), and the bulk property seems to get "smuggled in" via the dm id that is stored in the json or so - lets not duplicate it here for now
        return "com.mgmtp.a12.kernel.mmvalidators${visibility.packageSuffix()}".lowercase()
        // a prefix that does not rely on this behavior by the validation code generator would look e.g. like:
        // return "com.mgmtp.a12.kernel.mmvalidators${if (visibility == Visibility.PUBLIC) "" else ".extmpinternal"}.${modelName}_${version}_${variant}${if (isSpecialBulkVariation) "_bulk" else ""}".lowercase()
    }
}
fun makeValidatorsProjectConfig(mvv: ModelVersionVariant, rootDir: File): ValidatorsProjectConfig {
    return ValidatorsProjectConfig(
        modelName= mvv.modelName,
        version = mvv.version,
        variant = mvv.variant,
        visibility = determineVisibility(mvv, ArtifactType.VALIDATORS, rootDir),
        isSpecialBulkVariation = mvv.isSpecialBulkVariation
    )
}

data class TypingsProjectConfig(
    override val modelName: String,
    override val version: String,
    override val variant: String,
    override val visibility: Visibility,
    val isSpecialBulkVariation: Boolean,
    val externalIncludes: List<ExternalInclude>,
) : BaseProjectConfig {
    override fun subprojectName(): String {
        // we add the "mmtypings" prefix in the project name (in addition to the ":mmtypings:" part in the project path
        // because of https://github.com/gradle/gradle/issues/847)
        // we put the "internal" part at the front rather than at the back, because otherwise the "a12" variant would read "a12-internal" rather than "internal"
        return "mmtypings${visibility.artifactInfix()}-${modelName}-${version}${if (hideVariant()) "" else "-${variant}"}${if (isSpecialBulkVariation) "_bulk" else ""}".lowercase()
    // variant is not part of the project name (= artifact id) because 1) we currently only publish typings for one variant per dv, and 2) the variant should be transparent to the user, at least in a12
    }
    fun packagePrefix(): String {
        return "com.mgmtp.a12.kernel.mmtypings${visibility.packageSuffix()}.${modelName}_${version}${if(hideVariant()) "" else "_${variant}"}${if (isSpecialBulkVariation) "_bulk" else ""}".lowercase()
    }

    /**
     * For a12 public APIs, we don't want the fact that we have different variants to be part of the api, so we must omit the variant from the artifact ids and package paths.
     * Note: we could consider publishing artifacts both with and without explicit variant in this case, but this seems more complicated to implement, and there is likely no need for the explicit variant; also, the typings with the "none" variant contain all fields anyway
     */
    private fun hideVariant(): Boolean {
        return visibility == Visibility.PUBLIC && variant == MmCatalog.variantForPublicTypings
    }
}

fun makeTypingsProjectConfig(mvv: ModelVersionVariant, rootDir: File): TypingsProjectConfig {
    return TypingsProjectConfig(
        modelName = mvv.modelName,
        version = mvv.version,
        variant = mvv.variant,
        visibility = determineVisibility(mvv, ArtifactType.TYPINGS, rootDir),
        isSpecialBulkVariation = mvv.isSpecialBulkVariation,
        externalIncludes =  typingsThatHaveCustomExternalIncludes.get("${mvv.modelName}-${mvv.version}")?.invoke(mvv.variant, rootDir) ?: listOf(),
    )
}

enum class ArtifactType { DMJSONS, VALIDATORS, TYPINGS }

/**
 * Determines the visibility for an artifact based on the model version variant and artifact type.
 * - PUBLIC: only for typings of models in [MmCatalog.modelVersionsThatCanHavePublicArtifacts] with the public typings variant
 * - A12INTERNAL: for now only for dmjsons of models that have an entry in the SME versions file, with an SME-relevant variant
 * - INTERNAL: everything else
 *
 * @param rootDir project root dir, used to look up the SME versions file (cached via [loadSmeVersionKeys])
 */
fun determineVisibility(mvv: ModelVersionVariant, artifactType: ArtifactType, rootDir: File): Visibility {
    val modelVersionKey = "${mvv.modelName}-${mvv.version}"
    if (artifactType == ArtifactType.TYPINGS
        && modelVersionKey in MmCatalog.modelVersionsThatCanHavePublicArtifacts
        && mvv.variant == MmCatalog.variantForPublicTypings) {
        return Visibility.PUBLIC
    }
    if (artifactType == ArtifactType.DMJSONS
        && mvv.variant in MmCatalog.variantsForSmeDmjsons) {
        val smeKey = getKeyForVersionsFile(mvv.modelName, mvv.version)
        if (smeKey in loadSmeVersionKeys(rootDir)) {
            return Visibility.A12INTERNAL
        }
    }
    return Visibility.EXTMPINTERNAL
}

/**
 * Represents an include in a DM that is to be handled with TypedAccessorGenerator's "--external-models" parameter.
 * Additionally contains info that is needed to postprocess the DM json in order to reintroduce a proper DM include for
 * the corresponding group (the includes currently get expanded during dm-export and would be lost without this
 * postprocessing)
 *
 * @property dmId see [externalModelsSpec]. Example: "CT_MODEL_HEADER_1"
 * @property subprojThatProvidesExternalIncludeTypings the [TypingsProjectConfig] of the other subproject that provides
 * the typings for this external include. [subProjCfgForInclude] can be used to obtain it. This will be used to derive
 * both the [externalModelsSpec] and [depProjPath]. Example: `subProjCfgForInclude("CT_MODEL_HEADER-1", variant)`
 * @property rgNameIfSingleRgInclude: The name of the single root group of the included DM, if the include is a
 * single-rg include rather than a model-root include (which is currently always the case for our metamodels).
 * Gets passed to TypedAccessorGenerator via [externalModelsSpec].
 * @property groupFullName The path of the group to be replaced by an include of dm `<dmId>`. Example: `/content/Data` or `/header`
 */
data class ExternalInclude(
    val dmId: String,
    val subprojThatProvidesExternalIncludeTypings: TypingsProjectConfig,
    val rgNameIfSingleRgInclude: String?,
    val groupFullName: String,
) {
    /** Returns the spec in the format expected by TypedAccessorGenerator's `--external-models` parameter: `<dmId>=<packagePrefix>:<rgNameIfSingleRgInclude>` (or `<dmId>=<packagePrefix>` if [rgNameIfSingleRgInclude] is null) */
    val externalModelsSpec: String
        get() = "${dmId}=${subprojThatProvidesExternalIncludeTypings.packagePrefix()}${if (rgNameIfSingleRgInclude != null) ":${rgNameIfSingleRgInclude}" else ""}"

    /**
     * The project path (within this gradle build) of the subproject that provides the typings for the include, intended
     * to be used in a gradle "project" dependency.
     * example: `":mmtypings:mmtypings-CT_MODEL_HEADER-1"` or `":mmtypings:mmtypings-extmpinternal-SelectionModel-V1-a12"`
     */
    val depProjPath: String
        get() = ":mmtypings:${subprojThatProvidesExternalIncludeTypings.subprojectName()}"
}

enum class Visibility {
    EXTMPINTERNAL,
    A12INTERNAL,
    PUBLIC;

    fun artifactInfix(): String = when (this) {
        EXTMPINTERNAL -> "-extmpinternal"
        A12INTERNAL -> "-a12internal"
        PUBLIC -> ""
    }

    fun packageSuffix(): String = when (this) {
        EXTMPINTERNAL -> ".extmpinternal"
        A12INTERNAL -> ".a12internal"
        PUBLIC -> ""
    }

    fun resourcePrefix(): String = when (this) {
        EXTMPINTERNAL -> "extmpinternal/"
        A12INTERNAL -> "a12internal/"
        PUBLIC -> ""
    }
}

/**
 * the key of the outer map is the kernel_mm project root dir path. this is needed because the MmCatalog compiled
 * build script does not directly know the project's root dir. We do not really expect more than one entry in the
 * outer map.
 */
private val modelVersionVariantsFromFilesCache = mutableMapOf<File, Set<ModelVersionVariant>>()
fun modelVersionVariantsFromFiles(projRootDir: File): Set<ModelVersionVariant> {
    if(!modelVersionVariantsFromFilesCache.containsKey(projRootDir)) {
        modelVersionVariantsFromFilesCache.put(projRootDir, collectModelVersionVariantsFromDmFiles(projRootDir))
    }
    return modelVersionVariantsFromFilesCache.get(projRootDir)!!
}

private fun collectModelVersionVariantsFromDmFiles(rootDir: File): Set<ModelVersionVariant> {
    return buildSet {
        for ((modelName, version) in discoverConfiguredModelVersions(rootDir)) {
            val pattern = dmFileNamePattern(modelName, version)
            addAll(mergedFilesByModelVersion(rootDir, modelName, version).asSequence()
                .map { f ->
                    val m = pattern.matchEntire(f.name)
                    if (m != null) {
                        ModelVersionVariant(modelName=modelName, version=version,
                            variant = m.groups["variant"]!!.value,
                            isSpecialBulkVariation = m.groups["bulk"] != null)
                    } else {
                        error("unexpected file name pattern: ${f.name}")
                    }
                })
        }
    }
}