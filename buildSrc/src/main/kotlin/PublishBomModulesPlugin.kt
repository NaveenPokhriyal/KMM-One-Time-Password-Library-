import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

open class PublishBomModulesPlugin : DefaultTask() {

    @TaskAction
    fun publishBomModules() {
        getModulesPaths(project)
            .map { it.substring(1) }
            .map { File("$it/$PROPERTIES_FILE") }
            .filter { it.exists() }
            .forEach { propertiesFile ->
                bumpVersion(propertiesFile)
            }
    }

    private fun bumpVersion(propertiesFile: File) {
        var currVersion = getCurrentVersionOrEmpty(propertiesFile)
        if (currVersion.isEmpty()) {
            currVersion = "1"
        }
        val updatedVer = "${currVersion.toInt()+1}"
        logger.quiet("'${propertiesFile.path}' updated version -> $updatedVer")
        writeToFile(propertiesFile, currVersion, updatedVer)
    }

    private fun getCurrentDate(): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")
        return LocalDateTime.now().format(formatter)
    }

    private fun writeToFile(propertiesFile: File, currVersion: String, updatedVersion: String) {
        val updatedFile = propertiesFile
            .readText()
            .replace("$VERSION_PREFIX$currVersion", "$VERSION_PREFIX$updatedVersion")
        propertiesFile.writeText(updatedFile)
    }

    private fun isHotfixBranch(branchName: String) = branchName.startsWith("refs/heads/hotfix/")

    /* BoM has CalVer format: YYYY.MM.DD.{workflow run number} */
    private fun isBomModule(it: File) =
        it.path.startsWith(RETAIL_BOM) || it.path.startsWith(SUPPLY_BOM)

    /**
     * Extracts BoM-related modules in case of a single BoM publishing.
     */
    private fun List<String>.filterByBoM(bomToReleaseModulesPaths: List<String>): List<String> {
        return toMutableList().apply {
            if (bomToReleaseModulesPaths.size == 1) {
                // Remove Supply components if we release only Retail BOM
                if (bomToReleaseModulesPaths.contains(RETAIL_BOM)) {
                    removeAll(filter { it.contains("supply-components/") })
                }
                // Remove Retail components if we release only Supply BOM
                if (bomToReleaseModulesPaths.contains(SUPPLY_BOM)) {
                    removeAll(filter { it.contains("retail-components/") })
                    // Since Retail BOM is not under `retail-components` for now, we have to exclude it separately
                    remove(RETAIL_BOM)
                }
            }
        }.toList()
    }
}
