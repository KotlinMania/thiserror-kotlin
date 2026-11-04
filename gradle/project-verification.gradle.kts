import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.plugins.signing.Sign
import org.gradle.process.ExecOperations
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootEnvSpec
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension
import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnRootEnvSpec
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Base64
import java.util.Properties
import java.util.UUID
import java.util.zip.ZipInputStream
import org.gradle.api.publish.PublishingExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

buildscript {
    dependencies {
        classpath(files(rootProject.buildscript.configurations.getByName("classpath")))
    }
}

// Project-owned runtime checks remain callable without duplicating template infrastructure.
val kotlin = extensions.getByType(KotlinMultiplatformExtension::class.java)
val publishing = extensions.getByType(PublishingExtension::class.java)
val fullTargetBuildTaskNames =
    (rootProject.extra["template.fullTargetBuildTaskNames"] as Iterable<*>).map { it.toString() }
val isWindowsHost = System.getProperty("os.name").lowercase().contains("windows")
val isMacHost = System.getProperty("os.name").lowercase().contains("mac")
val projectCompileSdk = providers.gradleProperty("android.compileSdk").getOrElse("34")
val projectAndroidSdkDir = layout.projectDirectory.dir(".android-sdk").asFile
fun optionalTrimmedProperty(name: String): String? =
    providers.gradleProperty(name).orNull?.trim()?.takeIf { it.isNotEmpty() }

val androidCommandLineToolsRevision =
    providers
        .gradleProperty(
            "android.commandLineTools.revision",
        ).getOrElse("14742923")

val androidSdkOsName = if (isWindowsHost) "win" else if (isMacHost) "mac" else "linux"
fun downloadAndroidCommandLineTools(sdkDirectory: File = projectAndroidSdkDir, packageOs: String = androidSdkOsName) {
    val zipName = "commandlinetools-$packageOs-${androidCommandLineToolsRevision}_latest.zip"
    val url = "https://dl.google.com/android/repository/$zipName"
    val tmpDir = sdkDirectory.resolve(".tmp/commandline-tools")
    val zipFile = tmpDir.resolve(zipName)
    val latestDir = sdkDirectory.resolve("cmdline-tools/latest")
    println("setup-android-sdk: downloading $url")
    tmpDir.deleteRecursively()
    tmpDir.mkdirs()
    try {
        URI(url).toURL().openStream().use { input ->
            Files.copy(input, zipFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        latestDir.deleteRecursively()
        latestDir.mkdirs()
        val canonicalLatestDir = latestDir.canonicalFile.toPath()
        ZipInputStream(zipFile.inputStream().buffered()).use { zipInput ->
            generateSequence { zipInput.nextEntry }.forEach { entry ->
                val relativeName = entry.name.removePrefix("cmdline-tools/").trimStart('/')
                if (relativeName.isNotEmpty()) {
                    val target = latestDir.resolve(relativeName).canonicalFile
                    if (!target.toPath().startsWith(canonicalLatestDir)) {
                        throw GradleException("Refusing to extract Android SDK entry outside $latestDir: ${entry.name}")
                    }
                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile.mkdirs()
                        Files.copy(zipInput, target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                        if (packageOs != "win" && relativeName.startsWith("bin/")) target.setExecutable(true)
                    }
                }
                zipInput.closeEntry()
            }
        }
        if (packageOs != "win") latestDir.resolve("bin/sdkmanager").setExecutable(true)
    } finally {
        tmpDir.deleteRecursively()
    }
}

val publicationCompilationTasks =
    kotlin.targets
        .filter { it.name != "metadata" }
        .flatMap { target -> target.compilations.map { it.compileTaskProvider } }

val compileAllTargets =
    tasks.register("compileAllTargets") {
        group = "verification"
        description = "Compiles main and test sources for every declared target."
        dependsOn(publicationCompilationTasks)
        doLast {
            val skippedCompilations =
                publicationCompilationTasks.map { it.get() }.filter { task ->
                    !task.state.executed ||
                        (task.state.skipped && !task.state.upToDate && task.state.skipMessage != "FROM-CACHE")
                }
            check(skippedCompilations.isEmpty()) {
                "Required target compilations did not complete: " +
                    skippedCompilations.joinToString { "${it.path} (${it.state.skipMessage ?: "not executed"})" }
            }
        }
    }

val buildAllTargets =
    tasks.register("buildAllTargets") {
        group = "verification"
        description = "Compiles and links every declared target before runtime validation."
        dependsOn(compileAllTargets, fullTargetBuildTaskNames.filter { it != "swiftExportSmokeTest" })
    }

tasks.withType<AbstractTestTask>().configureEach {
    mustRunAfter(buildAllTargets)
}
tasks.named("swiftExportSmokeTest") { mustRunAfter(buildAllTargets) }

val publicationPomChecks =
    publishing.publications.withType<MavenPublication>().map { publication ->
        tasks.named("checkPomFileFor${publication.name.replaceFirstChar { it.uppercase() }}Publication")
    }

val checkPublicationPoms =
    tasks.register("checkPublicationPoms") {
        group = "verification"
        description = "Checks the generated POM for every Maven publication."
        dependsOn(publicationPomChecks)
        doLast {
            for (provider in publicationPomChecks) {
                val task = provider.get()
                task.state.rethrowFailure()
                check(
                    task.state.executed &&
                        (!task.state.skipped || task.state.upToDate || task.state.skipMessage == "FROM-CACHE"),
                ) {
                    "Required POM check did not execute: ${task.path} (${task.state.skipMessage})"
                }
            }
        }
    }

val publicationRuntimeTaskNames =
    listOf(
        "jvmTest",
        "testAndroidHostTest",
        "jsBrowserTest",
        "jsNodeTest",
        "wasmJsBrowserTest",
        "wasmJsNodeTest",
        "wasmWasiNodeTest",
        "macosArm64Test",
        "iosSimulatorArm64Test",
        "tvosSimulatorArm64Test",
        "watchosSimulatorArm64Test",
    )

val isCiBuild = providers.environmentVariable("CI").orNull?.lowercase() !in listOf(null, "", "false", "0")
val localAndroidX64Root = layout.buildDirectory.dir("local-android-x64")
val localAndroidX64GuestProject = optionalTrimmedProperty("project.validation.android.x64.guestProject")
val localAndroidX64AvdName = "thiserror_native_x64"
val useLocalAndroidX64Guest = isMacHost && !isCiBuild && localAndroidX64GuestProject != null
val setupLocalAndroidX64Runtime =
    tasks.register("setupLocalAndroidX64Runtime") {
        group = "setup"
        description = "Installs the local Linux x64 Android runtime for the existing Parallels guest."
        doLast {
            check(isMacHost && !isCiBuild) { "Android guest provisioning is only available in local macOS builds" }
            val guestProject =
                requireNotNull(localAndroidX64GuestProject) {
                    "Set project.validation.android.x64.guestProject to the existing guest's shared project path"
                }
            val root = localAndroidX64Root.get().asFile
            val sdk = root.resolve("sdk")
            val manager = sdk.resolve("cmdline-tools/latest/bin/sdkmanager")
            if (!manager.isFile) downloadAndroidCommandLineTools(sdk, "linux")
            val acceptedLicenses = projectAndroidSdkDir.resolve("licenses")
            check(acceptedLicenses.isDirectory) { "No previously accepted Android SDK licenses are available" }
            acceptedLicenses.listFiles()?.filter { it.isFile }?.forEach { license ->
                val destination = sdk.resolve("licenses/${license.name}")
                if (!destination.exists()) {
                    destination.parentFile.mkdirs()
                    license.copyTo(destination)
                }
            }
            val imagePackage = "system-images;android-$projectCompileSdk;default;x86_64"
            val image = sdk.resolve("system-images/android-$projectCompileSdk/default/x86_64/system.img")
            val execOperations = serviceOf<ExecOperations>()
            val sdkEnvironment =
                mapOf(
                    "REPO_OS_OVERRIDE" to "linux",
                    "JAVA_OPTS" to "-Dos.arch=amd64",
                    "JAVA_HOME" to System.getProperty("java.home"),
                    "ANDROID_HOME" to sdk.absolutePath,
                    "ANDROID_SDK_ROOT" to sdk.absolutePath,
                )
            if (!sdk.resolve("emulator/emulator").isFile || !sdk.resolve("platform-tools/adb").isFile || !image.isFile) {
                root.mkdirs()
                root.resolve("sdkmanager-install.log").outputStream().use { output ->
                    val result =
                        execOperations.exec {
                            commandLine(manager, "--sdk_root=${sdk.absolutePath}", "emulator", "platform-tools", imagePackage)
                            environment(sdkEnvironment)
                            standardInput = ByteArrayInputStream(ByteArray(0))
                            standardOutput = output
                            errorOutput = output
                            isIgnoreExitValue = true
                        }
                    check(result.exitValue == 0) {
                        "Local Android runtime installation failed; see ${root.resolve("sdkmanager-install.log")}. " +
                            "Any new license must be reviewed and accepted explicitly."
                    }
                }
            }
            val avdHome = root.resolve("avd").apply { mkdirs() }
            val avd = avdHome.resolve("$localAndroidX64AvdName.avd")
            if (!avd.resolve("config.ini").isFile) {
                execOperations
                    .exec {
                        commandLine(
                            sdk.resolve("cmdline-tools/latest/bin/avdmanager"),
                            "create",
                            "avd",
                            "--name",
                            localAndroidX64AvdName,
                            "--package",
                            imagePackage,
                            "--path",
                            avd.absolutePath,
                        )
                        environment(sdkEnvironment + ("ANDROID_AVD_HOME" to avdHome.absolutePath))
                        standardInput = ByteArrayInputStream("no\n".toByteArray())
                    }.assertNormalExitValue()
            }
            val avdLocation = avdHome.resolve("$localAndroidX64AvdName.ini")
            val location = Properties().apply { avdLocation.inputStream().use(::load) }
            location.setProperty("path", "$guestProject/build/local-android-x64/avd/$localAndroidX64AvdName.avd")
            location.remove("path.rel")
            avdLocation.outputStream().use { location.store(it, "Existing Linux guest AVD location") }
            println("Local Android x64 SDK and AVD prepared in $root; CI provisioning is disabled")
        }
    }

val foreignNativeRuntimeTasks =
    listOf("linuxArm64", "linuxX64", "mingwX64", "androidNativeArm64", "androidNativeX64").map { target ->
        tasks.register<Exec>("validate${target.replaceFirstChar { it.uppercase() }}Runtime") {
            group = "verification"
            description = "Runs the linked $target tests on its local host, guest or Android device."
            dependsOn("linkDebugTest${target.replaceFirstChar { it.uppercase() }}")
            mustRunAfter(buildAllTargets)
            val executable = if (target == "mingwX64") "test.exe" else "test.kexe"
            val platform = if (target == "mingwX64") "windows" else "linux"
            val runner = layout.projectDirectory.file("verification/run-native-tests.py")
            commandLine(
                providers.gradleProperty("project.validation.python").getOrElse("python3"),
                runner.asFile.absolutePath,
                "--target",
                target,
                "--binary",
                layout.buildDirectory
                    .file("bin/$target/debugTest/$executable")
                    .get()
                    .asFile.absolutePath,
                "--report-dir",
                layout.buildDirectory
                    .dir("reports/local-native-tests")
                    .get()
                    .asFile.absolutePath,
            )
            if (target.startsWith("androidNative")) {
                args(
                    "--adb",
                    optionalTrimmedProperty("project.validation.android.adb")
                        ?: projectAndroidSdkDir.resolve("platform-tools/adb${if (isWindowsHost) ".exe" else ""}").absolutePath,
                )
                val architecture = if (target.endsWith("Arm64")) "arm64" else "x64"
                optionalTrimmedProperty("project.validation.android.$architecture.serial")?.let { args("--serial", it) }
                if (target == "androidNativeX64" && useLocalAndroidX64Guest) {
                    dependsOn(setupLocalAndroidX64Runtime)
                    args(
                        "--guest-binary",
                        "$localAndroidX64GuestProject/build/bin/$target/debugTest/$executable",
                        "--android-guest-sdk",
                        "$localAndroidX64GuestProject/build/local-android-x64/sdk",
                        "--android-guest-avd-home",
                        "$localAndroidX64GuestProject/build/local-android-x64/avd",
                        "--android-guest-avd-name",
                        localAndroidX64AvdName,
                    )
                    optionalTrimmedProperty("project.validation.linux.vm")?.let { args("--vm", it) }
                }
            } else {
                optionalTrimmedProperty("project.validation.$platform.vm")?.let { args("--vm", it) }
                if (isMacHost && !isCiBuild) {
                    optionalTrimmedProperty("project.validation.$platform.guestProject")?.let {
                        args("--guest-binary", "$it/build/bin/$target/debugTest/$executable")
                    }
                }
            }
        }
    }

val validateAllAppleSimulators =
    tasks.register<Exec>("validateAllAppleSimulators") {
        group = "verification"
        description = "Runs the linked native tests on every available iOS, tvOS and watchOS simulator device."
        dependsOn("linkDebugTestIosSimulatorArm64", "linkDebugTestTvosSimulatorArm64", "linkDebugTestWatchosSimulatorArm64")
        mustRunAfter(buildAllTargets, publicationRuntimeTaskNames)
        commandLine(
            providers.gradleProperty("project.validation.python").getOrElse("python3"),
            layout.projectDirectory.file("verification/run-apple-simulator-tests.py").asFile,
            "--build-dir",
            layout.buildDirectory.get().asFile,
            "--report-dir",
            layout.buildDirectory
                .dir("reports/apple-simulator-matrix")
                .get()
                .asFile,
        )
    }

fun verifyRuntimeReports(name: String, reports: Set<File>) {
    check(reports.isNotEmpty()) { "Missing runtime reports for $name" }
    var tests = 0
    val parser =
        javax.xml.parsers.DocumentBuilderFactory
            .newInstance()
            .newDocumentBuilder()
    for (report in reports) {
        val suite = parser.parse(report).documentElement
        val count = suite.getAttribute("tests").toInt()
        check(suite.getElementsByTagName("testcase").length == count) {
            "$name: test count does not match the recorded cases in ${report.name}"
        }
        tests += count
        for (attribute in listOf("failures", "errors", "skipped")) {
            check(suite.getAttribute(attribute).ifEmpty { "0" }.toInt() == 0) {
                "$name: $attribute in ${report.name}"
            }
        }
        for (element in listOf("failure", "error", "skipped")) {
            check(suite.getElementsByTagName(element).length == 0) {
                "$name: $element result in ${report.name}"
            }
        }
    }
    check(tests > 0) { "Required runtime ran no tests: $name" }
}

tasks.matching { it.name in publicationRuntimeTaskNames || it.name == "connectedAndroidDeviceTest" }.configureEach {
    outputs.upToDateWhen { false }
    outputs.doNotCacheIf("Publication requires actual runtime execution") { true }
}
tasks.matching { it.name == "connectedAndroidDeviceTest" }.configureEach {
    mustRunAfter(buildAllTargets)
    doLast {
        verifyRuntimeReports(
            path,
            fileTree(layout.buildDirectory.dir("outputs/androidTest-results/connected")) {
                include("**/TEST-*.xml")
            }.files,
        )
    }
}

val verifyPublicationRuntimes =
    tasks.register("verifyPublicationRuntimes") {
        group = "verification"
        description = "Rejects skipped required test tasks and missing, empty or failing runtime reports."
        dependsOn(
            buildAllTargets,
            publicationRuntimeTaskNames,
            foreignNativeRuntimeTasks,
            validateAllAppleSimulators,
            "connectedAndroidDeviceTest",
            "swiftExportSmokeTest",
        )
        doLast {
            for (task in foreignNativeRuntimeTasks.map { it.get() } + validateAllAppleSimulators.get() +
                tasks.named("connectedAndroidDeviceTest").get() + tasks.named("swiftExportSmokeTest").get()) {
                task.state.rethrowFailure()
                check(task.state.executed && !task.state.skipped) {
                    "Required runtime did not execute: ${task.path} (${task.state.skipMessage})"
                }
            }
            for (name in publicationRuntimeTaskNames) {
                val task = tasks.named<AbstractTestTask>(name).get()
                task.state.rethrowFailure()
                check(task.state.executed && !task.state.skipped) {
                    "Required runtime did not execute: ${task.path} (${task.state.skipMessage})"
                }
                val reports =
                    fileTree(
                        task.reports.junitXml.outputLocation
                            .get()
                            .asFile,
                    ) { include("*.xml") }.files
                verifyRuntimeReports(task.path, reports)
            }
        }
    }

val publicationCheck =
    tasks.register("publicationCheck") {
        group = "verification"
        description = "Runs the retained all-platform verification suite when explicitly requested."
        dependsOn("build", compileAllTargets, checkPublicationPoms, verifyPublicationRuntimes)
        doLast {
            for (name in fullTargetBuildTaskNames + "build") {
                val task = tasks.named(name).get()
                task.state.rethrowFailure()
                check(
                    task.state.executed &&
                        (!task.state.skipped || task.state.upToDate || task.state.skipMessage == "FROM-CACHE"),
                ) {
                    "Required build task did not execute: ${task.path} (${task.state.skipMessage})"
                }
            }
        }
    }

