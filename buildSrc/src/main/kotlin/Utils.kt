import org.gradle.api.Project
import org.gradle.api.Task
import java.io.ByteArrayOutputStream
import java.io.File

internal fun Project.getPropertyOrEmpty(propertyName: String) = if (hasProperty(propertyName)) {
    properties[propertyName] as String
} else {
    ""
}

/**
 * Recursively traverse the gradle project and return all modules as a list.
 *
 * This does not capture node directories. Just leaf modules.
 *
 * @param project Gradle project
 * @param list to add relative path of individual modules
 */
internal fun getModulesPaths(project: Project): List<String> {
    val list = mutableListOf<Project>()
    getProjectsList(project, list)
    return list.map { it.path.replace(':', '/') }
}


private fun getProjectsList(project: Project, list: MutableList<Project>) {
    project.subprojects {
        // node - traverse inside
        if (this.subprojects.size > 0) {
            this.subprojects.forEach { sub ->
                getProjectsList(sub, list)
            }
        } else {
            // leaf - add to list
            list.add(this)
        }
    }
}

/**
 * Extracts version from the [propertiesFile].
 *
 * @return current version, empty string otherwise.
 */
internal fun getCurrentVersionOrEmpty(propertiesFile: File): String {
    if (!propertiesFile.exists()) return ""
    val versionLine = propertiesFile.useLines { it.toList() }.find { it.contains(VERSION_PREFIX) }
    return if (versionLine.isNullOrBlank()) {
        ""
    } else {
        versionLine.split("=")[1]
    }
}

internal fun isRetailPackagesAffected(changedModulesList: List<String>) =
    changedModulesList.any { it.contains(RETAIL_COMPONENTS) }

internal fun isSupplyPackagesAffected(changedModulesList: List<String>) =
    changedModulesList.any { it.contains(SUPPLY_COMPONENTS) }

internal fun renderModulesWithVersions(task: Task, publishedModules: List<String>): Map<String, String> {
    val publishedVersions = mutableMapOf<String, String>()
    publishedModules.forEach {
        val propertiesFile = File("$it/$PROPERTIES_FILE")
        val currentVersion = getCurrentVersionOrEmpty(propertiesFile)
        if (currentVersion.isNotEmpty()) {
            publishedVersions[it] = getCurrentVersionOrEmpty(propertiesFile)
        }
    }
    task.logger.quiet(
        """
                |============================== Publish results ===============================
                |       Published versions:  $publishedVersions
                |==============================================================================
                """.trimMargin()
    )

    return publishedVersions
}

/**
 * Function accepts changed file paths separated by comma and all the existing modules in the project.
 * @return only changed module paths based on input.
 */
internal fun getChangedModules(changedFiles: String, allModules: List<String>): MutableList<String> {
    val changedModulesList = mutableSetOf<String>()
    val changedFilesList = changedFiles.split(",")
    allModules.forEach { modulePath ->
        if (changedFilesList.any { changedFilePath -> changedFilePath.contains("$modulePath/") }) {
            changedModulesList.add(modulePath)
        }
    }
    return changedModulesList.toMutableList()
}

/**
 * Compare local version of `gradle.properties` file with the same on remote.
 *
 * @return version on remote or null otherwise (null means version on remote is not present).
 */
internal fun getRemoteVersion(project: Project, propertiesFile: File, currentVersion: String): String? {
    val output = ByteArrayOutputStream()
    project.exec {
        commandLine = ("git fetch origin $MAIN_BRANCH_NAME").split(" ")
    }
    project.exec {
        standardOutput = output
        commandLine = ("git diff origin/$MAIN_BRANCH_NAME -- ${propertiesFile.path}").split(" ")
    }
    val versionDiff = output.toString()
        .split("\r?\n|\r".toRegex())
        .filter { it.contains(VERSION_PREFIX) }
    project.logger.quiet("versionDiff: $versionDiff")
    return if (versionDiff.isEmpty()) {
        // version on master is the same (no diff)
        currentVersion
    } else if (versionDiff.size == 2) {
        versionDiff[0].split("=")[1].trim()
    } else {
        null
    }
}
