import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.StringReader
import java.net.URI
import java.security.MessageDigest
import java.time.LocalDate
import java.util.Properties
import java.util.zip.ZipInputStream
import javax.imageio.ImageIO
import javax.inject.Inject

plugins {
    application
}

val appName = providers.gradleProperty("app.name").get()
val appId = providers.gradleProperty("app.id").get()
val appVendor = providers.gradleProperty("app.vendor").get()
val appDescription = providers.gradleProperty("app.description").get()
val appIdentifier = providers.gradleProperty("app.identifier").get()
val appMainClass = providers.gradleProperty("app.mainClass").get()
val javaVersion = 25

group = "dev.davamu"
version = providers.gradleProperty("app.version").get()
description = appDescription

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(javaVersion)
    }
}

repositories {
    mavenCentral()
}

/** Developer tools that are not part of the application (e.g. icon generator). */
val tools: SourceSet = sourceSets.create("tools") {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().output + sourceSets.main.get().runtimeClasspath
}

dependencies {
    implementation(libs.bundles.ui)
    implementation(platform(libs.groovy.bom))
    implementation(libs.bundles.groovy)
    implementation(libs.bundles.cli)
    implementation(libs.bundles.core)
    implementation(libs.bundles.media)
    implementation(libs.bundles.native)

    // transitive of commons-vfs2: raise it above the version with CVE-2025-48924
    constraints {
        implementation(libs.commons.lang3)
    }

    runtimeOnly(libs.jaxb.runtime)
    runtimeOnly(libs.slf4j.nop)

    "toolsImplementation"(libs.picocli)
}


// ---------------------------------------------------------------------------------------------------------------------
// Build metadata
// ---------------------------------------------------------------------------------------------------------------------

/** Output of a git command (e.g. the number of commits or the commit hash), or the default value when git is not available. */
abstract class GitOutput : ValueSource<String, GitOutput.Parameters> {

    interface Parameters : ValueSourceParameters {
        val workingDir: DirectoryProperty
        val args: ListProperty<String>
        val defaultValue: Property<String>
    }

    @get:Inject
    abstract val exec: ExecOperations

    override fun obtain(): String = try {
        val output = ByteArrayOutputStream()
        val result = exec.exec {
            commandLine(listOf("git") + parameters.args.get())
            workingDir = parameters.workingDir.get().asFile
            standardOutput = output
            errorOutput = ByteArrayOutputStream()
            isIgnoreExitValue = true
        }
        if (result.exitValue == 0) output.toString().trim().ifEmpty { parameters.defaultValue.get() } else parameters.defaultValue.get()
    } catch (e: Exception) {
        parameters.defaultValue.get()
    }
}

// Build revision = number of commits reachable from HEAD (0 when git is not available)
// -Papp.revision overrides the git revision (e.g. Docker builds, where .git is not part of the build context)
val revision = providers.gradleProperty("app.revision").orElse(providers.of(GitOutput::class) {
    parameters.workingDir = layout.projectDirectory
    parameters.args = listOf("rev-list", "--count", "HEAD")
    parameters.defaultValue = "0"
})

// Short commit hash of the build (empty when git is not available), -Papp.commit overrides it
val commit = providers.gradleProperty("app.commit").orElse(providers.of(GitOutput::class) {
    parameters.workingDir = layout.projectDirectory
    parameters.args = listOf("rev-parse", "--short", "HEAD")
    parameters.defaultValue = ""
})

/**
 * API keys embedded into builds, read from api-keys.properties (git-ignored) or FILEBOT_PRO_APIKEY_* environment variables.
 * Only services whose terms allow one application key shared by all users. OMDb (daily quota per key) and Google Geocoding
 * (billed per key) are never embedded: users configure their own key at runtime.
 */
val apiKeyNames = listOf("themoviedb", "thetvdb", "fanart.tv", "acoustid", "anidb", "opensubtitles")

val apiKeys: Map<String, String> = run {
    val file = Properties()
    providers.fileContents(layout.projectDirectory.file("api-keys.properties")).asText.orNull?.let { file.load(StringReader(it)) }

    apiKeyNames.associateWith { name ->
        val env = providers.environmentVariable("FILEBOT_PRO_APIKEY_" + name.uppercase().replace('.', '_')).orNull
        (env ?: file.getProperty(name) ?: "").trim()
    }
}

val resourceTokens: Map<String, String> = mapOf(
    "application.name" to appName,
    "application.id" to appId,
    "application.version" to version.toString(),
    "application.revision" to revision.get(),
    "application.commit" to commit.get(),
    "application.vendor" to appVendor,
    "application.website" to providers.gradleProperty("app.website").get().trimEnd('/'),
    "update.url" to providers.gradleProperty("app.updateUrl").getOrElse(""),
) + apiKeys.mapKeys { "apikey." + it.key }

tasks.processResources {
    val tokens = resourceTokens
    val tokenPattern = Regex("@\\{([^}]+)}")

    inputs.properties(tokens)

    filesMatching("**/*.properties") {
        filter { line: String -> tokenPattern.replace(line) { tokens[it.groupValues[1]] ?: it.value } }
    }
}


// ---------------------------------------------------------------------------------------------------------------------
// Compile and run
// ---------------------------------------------------------------------------------------------------------------------

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = javaVersion
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:-options", "-Xlint:-removal"))
}

// build platform (jpackage cannot cross-build, every installer is built on its own OS)
val osName: String = System.getProperty("os.name")
val isWindows = osName.startsWith("Windows")
val isMac = osName.startsWith("Mac")
val platformId = when {
    isWindows -> "windows"
    isMac -> "macos"
    else -> "linux"
}
val isX11 = !isWindows && !isMac

// macOS builds exist for Apple Silicon and Intel, Windows builds for x64 and arm64 (the bundled runtime is the one of the build JDK)
val platformArch = if (System.getProperty("os.arch") in setOf("aarch64", "arm64")) "arm64" else "x64"
val platformSuffix = if (isMac || (isWindows && platformArch == "arm64")) "$platformId-$platformArch" else platformId

/** Packages opened for reflection-heavy libraries (json-io, Groovy, Glazed Lists) and Swing tweaks. */
val openPackages = listOf(
    "java.base/java.lang", "java.base/java.lang.reflect", "java.base/java.lang.invoke",
    "java.base/java.util", "java.base/java.util.function", "java.base/java.util.regex", "java.base/java.util.concurrent",
    "java.base/java.net", "java.base/java.io", "java.base/java.nio.file", "java.base/java.nio.file.attribute",
    "java.base/java.nio.channels", "java.base/java.nio.charset", "java.base/java.time", "java.base/java.time.chrono",
    "java.logging/java.util.logging", "java.prefs/java.util.prefs",
    "java.desktop/java.awt", "java.desktop/sun.awt", "java.desktop/sun.swing", "java.desktop/javax.swing.text.html",
)

val jvmFlags = listOf(
    "-Dfile.encoding=UTF-8",
    "-Djava.net.useSystemProxies=true",
    "-Dunixfs=false",
    "-DuseExtendedFileAttributes=true",
    "-DuseCreationDate=false",
    "--enable-native-access=ALL-UNNAMED",
    "--sun-misc-unsafe-memory-access=allow",
    // large file lists, caches and episode data hold many duplicate strings (paths, titles, MediaInfo values)
    "-XX:+UseStringDeduplication",
) + openPackages.map { "--add-opens=$it=ALL-UNNAMED" } + listOfNotNull(
    // Linux / BSD only (the package does not exist elsewhere): set WM_CLASS for the dock icon, see X11AppUtilities
    if (isX11) "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED" else null,
)

application {
    mainClass = appMainClass
    applicationName = appId
    applicationDefaultJvmArgs = jvmFlags
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to appMainClass,
            "Implementation-Title" to appName,
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to appVendor,
            "Build-Date" to LocalDate.now().toString(),
            "Build-Revision" to revision.get(),
            "Build-Commit" to commit.get(),
            "Enable-Native-Access" to "ALL-UNNAMED",
            "Add-Opens" to openPackages.joinToString(" "),
        )
    }
}


// ---------------------------------------------------------------------------------------------------------------------
// Native packages (jpackage)
//
//   gradlew jpackageImage    -> self-contained application folder (no Java installation required)
//   gradlew portableZip      -> zipped application folder
//   gradlew jpackage         -> native installer (msi/exe needs WiX on Windows, dmg on macOS, deb/rpm on Linux)
//                               override the type with -PinstallerType=exe|msi|dmg|pkg|deb|rpm
// ---------------------------------------------------------------------------------------------------------------------

val jdkHome = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(javaVersion) }.map { it.metadata.installationPath }
val jpackageExecutable = jdkHome.map { it.file(if (isWindows) "bin/jpackage.exe" else "bin/jpackage").asFile.path }

val jpackageInput = layout.buildDirectory.dir("jpackage/input")
val jpackageImageDir = layout.buildDirectory.dir("jpackage/image")
val jpackageInstallerDir = layout.buildDirectory.dir("jpackage/installer")
val jpackageResources = layout.buildDirectory.dir("jpackage/resources")
val packagingDir = layout.projectDirectory.dir("packaging")

/** JDK modules bundled with the native packages. */
val runtimeModules = listOf(
    "java.base", "java.compiler", "java.datatransfer", "java.desktop", "java.instrument", "java.logging", "java.management",
    "java.naming", "java.net.http", "java.prefs", "java.rmi", "java.scripting", "java.sql", "java.xml",
    "jdk.accessibility", "jdk.charsets", "jdk.crypto.cryptoki", "jdk.crypto.ec", "jdk.localedata", "jdk.management", "jdk.net",
    "jdk.unsupported", "jdk.zipfs",
)

val nativeToolsDir = layout.buildDirectory.dir("native/$platformId")

// optional native tools are bundled into all distributions and used by gradlew run
distributions {
    main {
        contents {
            from(nativeToolsDir) { into("native") }
        }
    }
}

tasks.named<JavaExec>("run") {
    systemProperty("dev.davamu.filebotpro.native.dir", nativeToolsDir.get().asFile.path)
}

val prepareJpackageInput = tasks.register<Sync>("prepareJpackageInput") {
    group = "distribution"
    from(tasks.jar)
    from(configurations.runtimeClasspath)
    // optional native tools (see downloadNativeTools)
    from(nativeToolsDir) { into("native") }
    // bash completion, linked to /usr/share/bash-completion/completions by the deb/rpm packages
    if (isX11) from(tasks.named("bashCompletion"))
    into(jpackageInput)
}

/** Linux packages install into /opt/<package name>; the deb/rpm templates link the command-line launcher to /usr/bin. */
val linuxInstallDir = "/opt/$appId"

// jpackage --resource-dir: WiX template overrides for the Windows installer (branded dialog bitmaps, shortcuts, PATH and Explorer integration dialog),
// deb/rpm templates for Linux (filebot-pro on the PATH, MediaInfo and fpcalc as recommended packages, dock icon),
// postinstall script for the macOS .pkg (filebot-pro on the PATH).
// The deb Depends line is fixed in packaging/linux/control instead of jpackage's PACKAGE_DEFAULT_DEPENDENCIES: those are
// looked up on the build machine, and ubuntu-latest names ALSA libasound2t64 (Ubuntu 24.04+, Debian 13+ only), which
// breaks installs on Ubuntu 22.04 and Debian 12. Review the list when the bundled runtime links new system libraries
val prepareJpackageResources = tasks.register<Sync>("prepareJpackageResources") {
    group = "distribution"
    // local values only: filter lambdas must not reference script properties (configuration cache)
    val windowsTokens = mapOf("installerDir" to packagingDir.dir("windows/installer").asFile.path, "appName" to appName, "appId" to appId)
    val linuxTokens = mapOf("appId" to appId, "installDir" to linuxInstallDir, "wmClass" to appId)
    val macTokens = mapOf("appName" to appName, "appId" to appId)
    val tokenPattern = Regex("@\\{([^}]+)}")
    inputs.property("windowsTokens", windowsTokens)
    inputs.property("linuxTokens", linuxTokens)
    inputs.property("macTokens", macTokens)
    from(packagingDir.dir("windows/installer")) {
        include("*.wxf", "*.wxs")
        filter { line: String -> tokenPattern.replace(line) { windowsTokens[it.groupValues[1]] ?: it.value } }
    }
    if (isX11) {
        from(packagingDir.dir("linux")) {
            exclude("*.properties", "apt/**")
            filter { line: String -> tokenPattern.replace(line) { linuxTokens[it.groupValues[1]] ?: it.value } }
            // jpackage looks up overrides by their public name
            rename("^desktop$", "$appName.desktop")
            rename("^spec$", "$appId.spec")
        }
    }
    if (isMac) {
        // .pkg postinstall script: links the command-line launcher to /usr/local/bin
        from(packagingDir.dir("mac")) {
            include("postinstall")
            filter { line: String -> tokenPattern.replace(line) { macTokens[it.groupValues[1]] ?: it.value } }
        }
    }
    into(jpackageResources)
}

fun jpackageArguments(type: String, dest: Provider<Directory>, deployment: String): List<String> = buildList {
    addAll(listOf("--type", type, "--dest", dest.get().asFile.path))
    addAll(listOf("--name", appName, "--app-version", version.toString(), "--vendor", appVendor))
    // Windows shows the launcher's FileDescription (taken from --description) as the process name in Task Manager
    addAll(listOf("--description", if (isWindows) appName else appDescription, "--copyright", "Copyright ${LocalDate.now().year} $appVendor"))
    addAll(listOf("--input", jpackageInput.get().asFile.path, "--main-jar", tasks.jar.get().archiveFileName.get(), "--main-class", appMainClass))
    addAll(listOf("--add-modules", runtimeModules.joinToString(",")))
    addAll(listOf("--jlink-options", "--strip-debug --no-man-pages --no-header-files --strip-native-commands"))

    val icon = packagingDir.file("icons/$appId." + if (isWindows) "ico" else if (isMac) "icns" else "png").asFile
    if (icon.exists()) addAll(listOf("--icon", icon.path))

    (jvmFlags + "-Dapplication.deployment=$deployment").forEach { addAll(listOf("--java-options", it)) }

    when {
        isWindows -> {
            // second launcher with an attached console for command-line usage (e.g. filebot-pro.exe -script fn:sysinfo)
            addAll(listOf("--add-launcher", "$appId=" + packagingDir.file("windows/console-launcher.properties").asFile.path))
            if (type != "app-image") {
                addAll(listOf("--resource-dir", jpackageResources.get().asFile.path))
                // desktop and Start menu shortcuts (GUI launcher only), the PATH entry and the Explorer integration, each one can be turned off in a setup dialog
                addAll(listOf("--win-menu", "--win-shortcut", "--win-shortcut-prompt", "--win-dir-chooser", "--win-menu-group", appName))
                addAll(listOf("--win-upgrade-uuid", "6f0c2a5e-1d8b-4c3e-9f57-2b1f0e7c4a91"))
            }
        }
        isMac -> {
            addAll(listOf("--mac-package-identifier", appIdentifier, "--mac-package-name", appName))
            // Finder "Open With" menu for video and subtitle files (opened by the Desktop open file handler)
            listOf("video", "subtitle").forEach { addAll(listOf("--file-associations", packagingDir.file("mac/$it.properties").asFile.path)) }
            if (type != "app-image") {
                addAll(listOf("--resource-dir", jpackageResources.get().asFile.path))
            }
        }
        else -> {
            // second launcher without a menu entry for command-line usage, linked to /usr/bin by the deb/rpm packages
            addAll(listOf("--add-launcher", "$appId=" + packagingDir.file("linux/cli-launcher.properties").asFile.path))
            if (type != "app-image") {
                addAll(listOf("--resource-dir", jpackageResources.get().asFile.path))
                addAll(listOf("--linux-package-name", appId, "--linux-shortcut", "--linux-menu-group", "AudioVideo;Video;Utility"))
                addAll(listOf("--linux-app-category", "video"))
                addAll(listOf("--about-url", providers.gradleProperty("app.website").get(), "--license-file", file("LICENSE.md").path))
                // jpackage rejects deb options for rpm and vice versa
                if (type == "rpm") {
                    addAll(listOf("--linux-rpm-license-type", "GPL-3.0"))
                }
                if (type == "deb") {
                    providers.gradleProperty("app.maintainerEmail").orNull?.takeIf { it.isNotBlank() }?.let {
                        addAll(listOf("--linux-deb-maintainer", it))
                    }
                }
            }
        }
    }
}

val cleanJpackageImage = tasks.register<Delete>("cleanJpackageImage") {
    delete(jpackageImageDir)
}

val jpackageImage = tasks.register<Exec>("jpackageImage") {
    group = "distribution"
    description = "Builds a self-contained application image with a bundled Java runtime."
    dependsOn(prepareJpackageInput, cleanJpackageImage)
    inputs.dir(jpackageInput)
    inputs.dir(packagingDir)
    outputs.dir(jpackageImageDir)

    executable = jpackageExecutable.get()
    args(jpackageArguments("app-image", jpackageImageDir, if (isWindows) "zip" else if (isMac) "app" else "tar"))
}

val portableZip = tasks.register<Zip>("portableZip") {
    group = "distribution"
    description = "Zips the self-contained application image."
    dependsOn(jpackageImage)
    from(jpackageImageDir)
    archiveFileName = "${appName.replace(' ', '-')}-${project.version}-$platformSuffix-portable.zip"
    destinationDirectory = layout.buildDirectory.dir("distributions")
}

val cleanJpackageInstaller = tasks.register<Delete>("cleanJpackageInstaller") {
    delete(jpackageInstallerDir)
}

tasks.register<Exec>("jpackage") {
    group = "distribution"
    description = "Builds a native installer for the current platform."
    dependsOn(prepareJpackageInput, prepareJpackageResources, cleanJpackageInstaller)
    inputs.dir(jpackageInput)
    inputs.dir(packagingDir)
    outputs.dir(jpackageInstallerDir)

    val type = providers.gradleProperty("installerType").getOrElse(if (isWindows) "msi" else if (isMac) "dmg" else "deb")
    val deployment = when (type) {
        "msi", "exe" -> "msi"
        "dmg", "pkg" -> "app"
        else -> type
    }

    executable = jpackageExecutable.get()
    args(jpackageArguments(type, jpackageInstallerDir, deployment))
}

// docs site (Astro + Starlight) -> docs/dist; needs Node.js
val docsDir = layout.projectDirectory.dir("docs")

val docsInstall = tasks.register<Exec>("docsInstall") {
    workingDir = docsDir.asFile
    inputs.file(docsDir.file("package-lock.json"))
    outputs.dir(docsDir.dir("node_modules"))
    commandLine(if (isWindows) listOf("cmd", "/c", "npm", "ci") else listOf("npm", "ci"))
}

val docsBuild = tasks.register<Exec>("docsBuild") {
    group = "documentation"
    description = "Builds the documentation site into docs/dist."
    dependsOn(docsInstall)
    workingDir = docsDir.asFile
    inputs.dir(docsDir.dir("src"))
    inputs.dir(docsDir.dir("public"))
    inputs.file(docsDir.file("astro.config.mjs"))
    outputs.dir(docsDir.dir("dist"))
    commandLine(if (isWindows) listOf("cmd", "/c", "npm", "run", "build") else listOf("npm", "run", "build"))
}

tasks.register("buildAll") {
    group = "distribution"
    description = "Builds everything for this platform: CLI distribution, portable zip, native installer and documentation."
    dependsOn(tasks.installDist, portableZip, "jpackage", docsBuild)
}


// ---------------------------------------------------------------------------------------------------------------------
// Packaging assets and optional native tools
// ---------------------------------------------------------------------------------------------------------------------

tasks.register<JavaExec>("generateIcons") {
    group = "distribution"
    description = "Renders packaging/icons (ico, icns, png), the Windows installer bitmaps and the in-app logos from packaging/icons/logo.png, logo.full.png and logo.full.dark.png (rendered from the SVG masters next to them)."
    classpath = tools.runtimeClasspath
    mainClass = "dev.davamu.filebotpro.tools.IconGenerator"
    args(
        packagingDir.dir("icons").asFile.path,
        layout.projectDirectory.dir("src/main/resources/dev/davamu/filebotpro/resources").asFile.path,
        packagingDir.dir("windows/installer").asFile.path,
        appId,
    )
}

tasks.register<JavaExec>("updateData") {
    group = "distribution"
    description = "Regenerates the bundled movie, series, subtitle, anime and disc indexes from Wikidata, the anime-offline-database and TheDiscDB (-Ponly=discdb for the disc index only)."
    classpath = tools.runtimeClasspath
    mainClass = "dev.davamu.filebotpro.tools.DataGenerator"
    args(listOfNotNull(layout.projectDirectory.dir("src/main/resources/dev/davamu/filebotpro/data").asFile.path, findProperty("only")?.toString()))
}

tasks.register<JavaExec>("bashCompletion") {
    group = "distribution"
    description = "Generates a bash completion script for the command-line interface."
    val output = layout.buildDirectory.file("packaging/${appId}_completion")
    outputs.file(output)
    classpath = tools.runtimeClasspath
    mainClass = "dev.davamu.filebotpro.platform.bash.BashCompletionBuilder"
    args(appId, output.get().asFile.path)
}

/**
 * Downloads MediaInfo (media properties) and Chromaprint fpcalc (AcoustID audio fingerprints) for Windows (x64 or arm64).
 * The files land in build/native/windows and are bundled into the jpackage image as app/native.
 * On macOS and Linux install them with the system package manager instead (e.g. brew install media-info chromaprint).
 */
object Downloads {
    /** Downloads [url] into a temporary file and fails unless its SHA-256 matches [sha256]. */
    fun downloadVerified(url: String, sha256: String, logger: Logger): File {
        logger.lifecycle("Download $url")
        val file = File.createTempFile("download", ".tmp")
        val digest = MessageDigest.getInstance("SHA-256")
        URI(url).toURL().openStream().use { input ->
            file.outputStream().use { out ->
                val buffer = ByteArray(65536)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                    out.write(buffer, 0, n)
                }
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (!actual.equals(sha256, ignoreCase = true)) {
            file.delete()
            throw GradleException("Checksum mismatch for $url: expected $sha256 but was $actual")
        }
        return file
    }
}

abstract class DownloadNativeTools : DefaultTask() {

    @get:Input
    abstract val archives: MapProperty<String, String>

    /** SHA-256 of every archive and file, URL -> hex digest (a new version must be pinned together with its checksum) */
    @get:Input
    abstract val checksums: MapProperty<String, String>

    /** Plain files (e.g. license texts that are not part of an archive), URL -> relative path */
    @get:Input
    abstract val files: MapProperty<String, String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun download() {
        val folder = outputDir.get().asFile
        folder.mkdirs()

        archives.get().forEach { (url, file) ->
            var found = false
            val downloaded = Downloads.downloadVerified(url, checksums.get().getValue(url), logger)
            downloaded.inputStream().use { input ->
                ZipInputStream(input).use { zip ->
                    generateSequence { zip.nextEntry }.forEach { entry ->
                        val name = entry.name.substringAfterLast('/')
                        val target = when {
                            name.equals(file, ignoreCase = true) -> File(folder, file).also { found = true }
                            name.contains("license", ignoreCase = true) || name.startsWith("COPYING") -> File(folder, "licenses/${file.substringBeforeLast('.')}-$name")
                            else -> null
                        }
                        target?.let {
                            it.parentFile.mkdirs()
                            it.outputStream().use { out -> zip.copyTo(out) }
                        }
                    }
                }
            }
            downloaded.delete()
            if (!found) {
                throw GradleException("$file not found in $url")
            }
        }

        files.get().forEach { (url, path) ->
            val target = File(folder, path)
            target.parentFile.mkdirs()
            val downloaded = Downloads.downloadVerified(url, checksums.get().getValue(url), logger)
            downloaded.copyTo(target, overwrite = true)
            downloaded.delete()
        }
    }
}

tasks.register<DownloadNativeTools>("downloadNativeTools") {
    group = "distribution"
    description = "Downloads MediaInfo, fpcalc, mkvpropedit and AtomicParsley for Windows (x64 or arm64) into build/native/windows."
    // MediaInfo.dll is loaded into the Java process and must match its architecture, the command-line tools are x64 only
    // and run through x64 emulation on Windows 11 arm64
    val mediaInfoArch = if (platformArch == "arm64") "ARM64" else "x64"
    archives.put("https://mediaarea.net/download/binary/libmediainfo0/26.10/MediaInfo_DLL_26.10_Windows_${mediaInfoArch}_WithoutInstaller.zip", "MediaInfo.dll")
    archives.put("https://github.com/acoustid/chromaprint/releases/download/v1.6.1/chromaprint-fpcalc-1.6.1-windows-x86_64.zip", "fpcalc.exe")
    files.put("https://raw.githubusercontent.com/acoustid/chromaprint/v1.6.1/LICENSE.md", "licenses/fpcalc-LICENSE.md")
    // optional tools for --apply metadata (Matroska tags and MP4 atoms)
    archives.put("https://mkvtoolnix.download/windows/releases/102.0/mkvtoolnix-64-bit-102.0.zip", "mkvpropedit.exe")
    archives.put("https://github.com/wez/atomicparsley/releases/download/20240608.083822.1ed9031/AtomicParsleyWindows.zip", "AtomicParsley.exe")
    files.put("https://raw.githubusercontent.com/wez/atomicparsley/20240608.083822.1ed9031/COPYING", "licenses/AtomicParsley-COPYING")
    outputDir = layout.buildDirectory.dir("native/windows")

    // SHA-256 of the pinned downloads (these tools are bundled into the installers and run by the application)
    checksums.put("https://mediaarea.net/download/binary/libmediainfo0/26.10/MediaInfo_DLL_26.10_Windows_x64_WithoutInstaller.zip", "4efe172df014693732e8b64a004cf382b8599eaf86be26a9b38c0d2993e60e1d")
    checksums.put("https://mediaarea.net/download/binary/libmediainfo0/26.10/MediaInfo_DLL_26.10_Windows_ARM64_WithoutInstaller.zip", "5783ce4e12479933394d67cce3f3c3042e84a644e223798d56ede9ac7449148e")
    checksums.put("https://github.com/acoustid/chromaprint/releases/download/v1.6.1/chromaprint-fpcalc-1.6.1-windows-x86_64.zip", "735d6182b38e9f364b84ce6f4ccd682c75e2851de89735711d6b762d12b92a4e")
    checksums.put("https://mkvtoolnix.download/windows/releases/102.0/mkvtoolnix-64-bit-102.0.zip", "c02e918900f6d945d9307b426237e456378b79a200589ac6928de39064409a44")
    checksums.put("https://github.com/wez/atomicparsley/releases/download/20240608.083822.1ed9031/AtomicParsleyWindows.zip", "cc7c5a7dcaba28869f3819e4a57f382dc0ff307b7b013f9bffd028605ae6f52d")
    checksums.put("https://raw.githubusercontent.com/acoustid/chromaprint/v1.6.1/LICENSE.md", "562cfe59627e0c4e8e3b066f3ff2e9736f83811ffe4c6c2c7796595aa7595ebd")
    checksums.put("https://raw.githubusercontent.com/wez/atomicparsley/20240608.083822.1ed9031/COPYING", "b9caf512b5d83f7e161ab62bdf9c915f4771e3bbc95a2386a1d247f80a01fe67")
}


// ---------------------------------------------------------------------------------------------------------------------
// Synology DSM 7 package (command-line tool with a bundled Java runtime)
// ---------------------------------------------------------------------------------------------------------------------

/**
 * Downloads a file once (e.g. the Java runtime for another platform).
 */
abstract class DownloadFile : DefaultTask() {

    @get:Input
    abstract val url: Property<String>

    @get:Input
    abstract val sha256: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun download() {
        val target = outputFile.get().asFile
        target.parentFile.mkdirs()
        val downloaded = Downloads.downloadVerified(url.get(), sha256.get(), logger)
        downloaded.copyTo(target, overwrite = true)
        downloaded.delete()
    }
}

// -PsynologyArch=x86_64 (Intel and AMD models) or armv8 (64-bit ARM models), see https://kb.synology.com/en-global/DSM/tutorial/What_kind_of_CPU_does_my_NAS_have
val synologyArch = providers.gradleProperty("synologyArch").getOrElse("x86_64")
val synologyJavaArch = mapOf("x86_64" to "x64", "armv8" to "aarch64")[synologyArch] ?: throw GradleException("Unsupported synologyArch: $synologyArch (x86_64, armv8)")
val synologyDir = layout.buildDirectory.dir("synology/$synologyArch")

val downloadSynologyRuntime = tasks.register<DownloadFile>("downloadSynologyRuntime") {
    group = "distribution"
    description = "Downloads the Temurin Java runtime for the Synology package."
    // pinned release (the "latest" endpoint changes under our feet): update the URL and the checksum together
    url = "https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.4.1%2B1/OpenJDK25U-jre_${synologyJavaArch}_linux_hotspot_25.0.4.1_1.tar.gz"
    sha256 = mapOf(
        "x64" to "1731a34baadec5479258ea0202e4d5d865d2efeee60cb0c7d7eb056fe96ca219",
        "aarch64" to "34828cbb93ed31c281c84ecb31ddab655d11a802f263c1fc019d42e9e0230fed",
    ).getValue(synologyJavaArch)
    outputFile = layout.buildDirectory.file("synology/jre-linux-$synologyJavaArch.tar.gz")
}

val synologyTokens = mapOf(
    "appId" to appId,
    "appName" to appName,
    "vendor" to appVendor,
    "description" to appDescription,
    "website" to providers.gradleProperty("app.website").get(),
    "arch" to synologyArch,
    // DSM expects x.y.z-build
    "version" to "${project.version}-${revision.get().padStart(4, '0')}",
)

val synologyPackageArchive = tasks.register<Tar>("synologyPackageArchive") {
    description = "Builds package.tgz of the Synology package."
    dependsOn(tasks.installDist, downloadSynologyRuntime)
    compression = Compression.GZIP
    archiveFileName = "package.tgz"
    destinationDirectory = synologyDir

    // the Windows native tools are of no use on the NAS
    from(tasks.installDist.map { it.destinationDir }) {
        exclude("native/**")
        // the start script loses its executable flag when the package is built on Windows
        filesMatching("bin/*") { permissions { unix("rwxr-xr-x") } }
        into("app")
    }
    from(tarTree(downloadSynologyRuntime.flatMap { it.outputFile })) {
        // jdk-25+x-jre/... -> jre/...
        eachFile {
            relativePath = RelativePath(true, "jre", *relativePath.segments.drop(1).toTypedArray())
            // executable flags get lost when the package is built on Windows
            if (relativePath.segments[1] == "bin" || name == "jspawnhelper" || name == "jexec") {
                permissions { unix("rwxr-xr-x") }
            }
        }
        includeEmptyDirs = false
    }
    val id = appId
    from(packagingDir.dir("synology/bin")) {
        rename("launcher", id)
        filter { line: String -> line.replace("@{appId}", id) }
        filePermissions { unix("rwxr-xr-x") }
        into("bin")
    }
}

val synologyIcons = tasks.register("synologyIcons") {
    description = "Renders the package icons of the Synology package."
    val source = packagingDir.file("icons/$appId.png")
    val target = synologyDir.map { it.dir("icons") }
    inputs.file(source)
    outputs.dir(target)
    doLast {
        val image = ImageIO.read(source.asFile)
        mapOf("PACKAGE_ICON.PNG" to 64, "PACKAGE_ICON_256.PNG" to 256).forEach { (name, size) ->
            val icon = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
            val g = icon.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.drawImage(image, 0, 0, size, size, null)
            g.dispose()
            ImageIO.write(icon, "png", target.get().file(name).asFile.also { it.parentFile.mkdirs() })
        }
    }
}

tasks.register<Tar>("synologyPackage") {
    group = "distribution"
    description = "Builds the Synology DSM 7 package (-PsynologyArch=x86_64|armv8) into build/distributions."
    dependsOn(synologyPackageArchive, synologyIcons)
    compression = Compression.NONE
    archiveFileName = "${appName.replace(' ', '-')}-${project.version}-synology-$synologyArch.spk"
    destinationDirectory = layout.buildDirectory.dir("distributions")

    val tokenPattern = Regex("@\\{([^}]+)}")
    val tokens = synologyTokens
    inputs.property("synologyTokens", tokens)

    from(synologyPackageArchive)
    from(synologyIcons)
    from(packagingDir.dir("synology")) {
        include("INFO", "conf/**")
        filter { line: String -> tokenPattern.replace(line) { tokens[it.groupValues[1]] ?: it.value } }
    }
    from(packagingDir.dir("synology/scripts")) {
        filePermissions { unix("rwxr-xr-x") }
        into("scripts")
    }
}
