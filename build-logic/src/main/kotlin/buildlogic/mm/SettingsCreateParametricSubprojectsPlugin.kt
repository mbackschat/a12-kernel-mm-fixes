package buildlogic.mm

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import java.io.File

/**
 * settings plugin that dynamically creates subprojects for mmtypings, mmdmjsons and mmvalidators based on the DMs that
 * are discovered via [MmCatalog] based on the files in `documentModelsOs` and `documentModels`.
 *
 * Note: the _configuration_ of these subprojects happens separately, in the root build.gradle
 */
class SettingsCreateParametricSubprojectsPlugin : Plugin<Settings> {
    override fun apply(settings: Settings) {
        val mmtypingsParent = ":mmtypings"
        settings.include(mmtypingsParent)
        settings.project(mmtypingsParent).projectDir = File(settings.settingsDir, "build/mmtypings/mmtypingsPlaceholderRootProjectDir").apply { mkdirs() }

        MmCatalog.typingsProjConfigs(settings.settingsDir).keys.forEach { typingsProjName ->
            val subprojPath = ":mmtypings:$typingsProjName"
            settings.include(subprojPath)
            settings.project(subprojPath).projectDir = File(settings.settingsDir, "build/mmtypings/$typingsProjName").apply { mkdirs() }
        }


        val mmdmjsonsParent = ":mmdmjsons"
        settings.include(mmdmjsonsParent)
        settings.project(mmdmjsonsParent).projectDir = File(settings.settingsDir, "build/mmdmjsons/mmdmjsonsPlaceholderRootProjectDir").apply { mkdirs() }

        MmCatalog.dmjsonsProjConfigs(settings.settingsDir).keys.forEach { dmjsonsProjName ->
            val subprojPath = ":mmdmjsons:${dmjsonsProjName}"
            settings.include(subprojPath)
            settings.project(subprojPath).projectDir = File(settings.settingsDir, "build/mmdmjsons/$dmjsonsProjName").apply { mkdirs() }
        }


        val mmvalidatorsParent = ":mmvalidators"
        settings.include(mmvalidatorsParent)
        settings.project(mmvalidatorsParent).projectDir = File(settings.settingsDir, "build/mmvalidators/mmvalidatorsPlaceholderRootProjectDir").apply { mkdirs() }

        MmCatalog.validatorsProjConfigs(settings.settingsDir).keys.forEach { validatorsProjName ->
            val subprojPath = ":mmvalidators:$validatorsProjName"
            settings.include(subprojPath)
            settings.project(subprojPath).projectDir = File(settings.settingsDir, "build/mmvalidators/$validatorsProjName").apply { mkdirs() }
        }
    }
}
