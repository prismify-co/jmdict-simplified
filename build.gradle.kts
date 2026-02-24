import de.undercouch.gradle.tasks.download.Download
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

group = "org.edrdg.jmdict.simplified"
version = "3.5.0"

val jmdictLanguages = listOf( // ISO 639-2, 3-letter codes
    "all", "eng", "eng-common",
    // Other languages, in order of # of entries:
    "ger", "rus", "hun", "dut", "spa", "fre", "swe", "slv",
)
val jmdictReportFile = "jmdict-release-info.md"

val jmnedictLanguages = listOf("all") // There is only English
val jmnedictReportFile = "jmnedict-release-info.md"

val kanjidicLanguages = listOf("all", "en") // ISO 639-1, 2-letter codes
val kanjidicReportFile = "kanjidic-release-info.md"

plugins {
    id("de.undercouch.download") version "5.3.0"
    kotlin("jvm") version "1.8.0"
    kotlin("plugin.serialization") version "1.8.0"
    application
}

application {
    mainClass.set("org.edrdg.jmdict.simplified.MainKt")
    applicationDefaultJvmArgs = listOf("-Xmx6g", "-Djdk.xml.entityExpansionLimit=0")
}

tasks {
    // See <https://www.baeldung.com/kotlin/gradle-executable-jar>
    val uberJar = register<Jar>("uberJar") {
        // We need this for Gradle optimization to work
        dependsOn.addAll(listOf("compileJava", "compileKotlin", "processResources"))
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        manifest { attributes(mapOf("Main-Class" to application.mainClass)) }
        val sourcesMain = sourceSets.main.get()
        val runtime = configurations.runtimeClasspath.get()
            .map { if (it.isDirectory) it else zipTree(it) }
        from(runtime + sourcesMain.output)
    }
    build {
        dependsOn(uberJar)
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "13"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.slf4j:slf4j-simple:2.0.6")
    implementation("io.github.microutils:kotlin-logging:3.0.4")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.1")
    implementation("com.github.ajalt.clikt:clikt:3.5.0")
    implementation("net.swiftzer.semver:semver:1.2.0")
}

sourceSets {
    main {
        resources {
            srcDir("$buildDir/generated-resources")
        }
    }
}

val createDictXmlDir: Task by tasks.creating {
    val dictXmlDir = "$buildDir/dict-xml"
    extra["dictXmlDir"] = dictXmlDir
    doLast {
        mkdir(dictXmlDir)
    }
}

val jmdictDownload by tasks.creating(Download::class) {
    group = "Download"
    description = "Download JMdict source XML archive"
    val dictXmlDir: String by createDictXmlDir.extra
    val filePath = "$dictXmlDir/JMdict.gz"
    src("http://ftp.edrdg.org/pub/Nihongo/JMdict.gz")
    dest(filePath)
    extra["archivePath"] = filePath
    overwrite(true)
    onlyIfModified(true)
}

val jmdictExtract: Task by tasks.creating {
    group = "Extract"
    description = "Extract JMdict source XML from an archive"
    dependsOn(jmdictDownload)
    val dictXmlDir: String by createDictXmlDir.extra
    val archivePath: String by jmdictDownload.extra
    val filePath = "$dictXmlDir/JMdict.xml"
    extra["jmdictPath"] = filePath
    doLast {
        resources.gzip(archivePath).read().copyTo(file(filePath).outputStream())
    }
}

val jmnedictDownload by tasks.creating(Download::class) {
    group = "Download"
    description = "Download JMnedict source XML archive"
    val dictXmlDir: String by createDictXmlDir.extra
    val filePath = "$dictXmlDir/JMnedict.xml.gz"
    src("http://ftp.edrdg.org/pub/Nihongo/JMnedict.xml.gz")
    dest(filePath)
    extra["archivePath"] = filePath
    overwrite(true)
    onlyIfModified(true)
}

val jmnedictExtract: Task by tasks.creating {
    group = "Extract"
    description = "Extract JMnedict source XML from an archive"
    dependsOn(jmnedictDownload)
    val dictXmlDir: String by createDictXmlDir.extra
    val archivePath: String by jmnedictDownload.extra
    val filePath = "$dictXmlDir/JMnedict.xml"
    extra["jmnedictPath"] = filePath
    doLast {
        resources.gzip(archivePath).read().copyTo(file(filePath).outputStream())
    }
}

val kanjidicDownload by tasks.creating(Download::class) {
    group = "Download"
    description = "Download Kanjidic source XML archive"
    val dictXmlDir: String by createDictXmlDir.extra
    val filePath = "$dictXmlDir/kanjidic2.xml.gz"
    src("http://www.edrdg.org/kanjidic/kanjidic2.xml.gz")
    dest(filePath)
    extra["archivePath"] = filePath
    overwrite(true)
    onlyIfModified(true)
}

val kanjidicExtract: Task by tasks.creating {
    group = "Extract"
    description = "Extract Kanjidic source XML from an archive"
    dependsOn(kanjidicDownload)
    val dictXmlDir: String by createDictXmlDir.extra
    val archivePath: String by kanjidicDownload.extra
    val filePath = "$dictXmlDir/kanjidic2.xml"
    extra["kanjidicPath"] = filePath
    doLast {
        resources.gzip(archivePath).read().copyTo(file(filePath).outputStream())
    }
}

val kradfileDownload by tasks.creating(Download::class) {
    group = "Download"
    description = "Download KRADFILE2/RADKFILE2 archive"
    val dictXmlDir: String by createDictXmlDir.extra
    val filePath = "$dictXmlDir/kradzip.zip"
    src("http://ftp.edrdg.org/pub/Nihongo/kradzip.zip")
    dest(filePath)
    extra["archivePath"] = filePath
    overwrite(true)
    onlyIfModified(true)
}

val kradfileExtract by tasks.creating(Copy::class) {
    group = "Extract"
    description = "Extract KRADFILE2/RADKFILE2 sources from an archive"
    dependsOn(kradfileDownload)
    val dictXmlDir: String by createDictXmlDir.extra
    val archivePath: String by kradfileDownload.extra
    val kradfilePath = "$dictXmlDir/kradfile"
    val kradfile2Path = "$dictXmlDir/kradfile2"
    val radkfilexPath = "$dictXmlDir/radkfilex"
    extra["kradfilePath"] = kradfilePath
    extra["kradfile2Path"] = kradfile2Path
    extra["radkfilexPath"] = radkfilexPath
    from(
        zipTree(archivePath)
    )
    into(dictXmlDir)
}

val jlptDownload by tasks.creating(Download::class) {
    group = "Download"
    description = "Download JLPT N-level kanji data from AnchorI/jlpt-kanji-dictionary"
    val dictXmlDir: String by createDictXmlDir.extra
    val filePath = "$dictXmlDir/jlpt-kanji.json"
    src("https://raw.githubusercontent.com/AnchorI/jlpt-kanji-dictionary/main/jlpt-kanji.json")
    dest(filePath)
    extra["jlptJsonPath"] = filePath
    overwrite(true)
    onlyIfModified(true)
}

val jlptGenerateTsv: Task by tasks.creating {
    group = "Download"
    description = "Generate JLPT N-level TSV resource from downloaded JSON"
    dependsOn(jlptDownload)
    val jlptJsonPath: String by jlptDownload.extra
    val outputDir = "$buildDir/generated-resources"
    val tsvPath = "$outputDir/jlpt-n-levels.tsv"
    outputs.file(tsvPath)
    doLast {
        mkdir(outputDir)
        val jsonText = file(jlptJsonPath).readText()
        @Suppress("UNCHECKED_CAST")
        val entries = groovy.json.JsonSlurper().parseText(jsonText) as List<Map<String, Any?>>
        val byLevel = mutableMapOf<Int, StringBuilder>()
        for (entry in entries) {
            val kanji = entry["kanji"] as? String ?: continue
            val jlptStr = entry["jlpt"] as? String ?: continue
            val level = jlptStr.removePrefix("N").toIntOrNull() ?: continue
            byLevel.getOrPut(level) { StringBuilder() }.append(kanji)
        }
        val tsv = buildString {
            appendLine("# JLPT N-level kanji data (auto-generated from AnchorI/jlpt-kanji-dictionary)")
            appendLine("# Source: MIT license, derived from tanos.co.uk (CC-BY)")
            appendLine("# Format: LEVEL<tab>KANJI_STRING")
            appendLine("# Generated by: ./gradlew jlptGenerateTsv")
            for (level in 5 downTo 1) {
                val kanji = byLevel[level] ?: continue
                appendLine("$level\t$kanji")
            }
        }
        file(tsvPath).writeText(tsv)
        println("Generated JLPT N-level TSV: ${byLevel.values.sumOf { it.length }} kanji across ${byLevel.size} levels")
    }
}

/**
 * Download and extract all dictionaries
 */
val download: Task by tasks.creating {
    group = "Download"
    description = "Download and unpack all dictionaries"
    dependsOn(jmdictExtract, jmnedictExtract, kanjidicExtract, kradfileExtract, jlptGenerateTsv)
}

tasks.named("processResources") {
    dependsOn(jlptGenerateTsv)
}

fun getFileHash(inputFilePath: String): String {
    try {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(file(inputFilePath).readBytes())
            .joinToString("") { b -> (0xFF and b.toInt()).toString(16) }
    } catch (e: NoSuchAlgorithmException) {
        throw Exception("SHA-256 is not supported in this Java instance", e)
    }
}

val jmdictUpdateChecksumFile: Task by tasks.creating {
    group = "Checksum"
    description = "Generate a checksum of JMdict XML file and write it into a checksum file"
    val jmdictPath: String by jmdictExtract.extra
    val jmdictChecksumPath = "$projectDir/checksums/JMdict.xml.sha256"
    extra["jmdictChecksumPath"] = jmdictChecksumPath
    doLast {
        file(jmdictChecksumPath).writeText(getFileHash(jmdictPath))
    }
}

val jmnedictUpdateChecksumFile: Task by tasks.creating {
    group = "Checksum"
    description = "Generate a checksum of JMnedict XML file and write it into a checksum file"
    val jmnedictPath: String by jmnedictExtract.extra
    val jmnedictChecksumPath = "$projectDir/checksums/JMnedict.xml.sha256"
    extra["jmnedictChecksumPath"] = jmnedictChecksumPath
    doLast {
        file(jmnedictChecksumPath).writeText(getFileHash(jmnedictPath))
    }
}

val kanjidicUpdateChecksumFile: Task by tasks.creating {
    group = "Checksum"
    description = "Generate a checksum of Kanjidic XML file and write it into a checksum file"
    val kanjidicPath: String by kanjidicExtract.extra
    val kanjidicChecksumPath = "$projectDir/checksums/kanjidic2.xml.sha256"
    extra["kanjidicChecksumPath"] = kanjidicChecksumPath
    doLast {
        file(kanjidicChecksumPath).writeText(getFileHash(kanjidicPath))
    }
}

/**
 * We don't do change checks for kradfile and radkfile, as they change rarely.
 * Instead, we expect other dictionaries to change, which triggers conversion
 * of kradfile and radkfile.
 */
val updateChecksums: Task by tasks.creating {
    group = "Checksum"
    description = "Generate checksums of all dictionaries and write into checksum files"
    dependsOn(jmdictUpdateChecksumFile, jmnedictUpdateChecksumFile, kanjidicUpdateChecksumFile)
}

val jmdictHasChanged: Task by tasks.creating {
    group = "Checksum"
    description = "Check if a checksum of JMdict XML file has changed"
    val jmdictPath: String by jmdictExtract.extra
    val jmdictChecksumPath: String by jmdictUpdateChecksumFile.extra
    doLast {
        val previousChecksum = file(jmdictChecksumPath).readText().trim()
        val newChecksum = getFileHash(jmdictPath).trim()
        println(if (previousChecksum == newChecksum) "NO" else "YES")
    }
}

val jmnedictHasChanged: Task by tasks.creating {
    group = "Checksum"
    description = "Check if a checksum of JMnedict XML file has changed"
    val jmnedictPath: String by jmnedictExtract.extra
    val jmnedictChecksumPath: String by jmnedictUpdateChecksumFile.extra
    doLast {
        val previousChecksum = file(jmnedictChecksumPath).readText().trim()
        val newChecksum = getFileHash(jmnedictPath).trim()
        println(if (previousChecksum == newChecksum) "NO" else "YES")
    }
}

val kanjidicHasChanged: Task by tasks.creating {
    group = "Checksum"
    description = "Check if a checksum of Kanjidic XML file has changed"
    val kanjidicPath: String by kanjidicExtract.extra
    val kanjidicChecksumPath: String by kanjidicUpdateChecksumFile.extra
    doLast {
        val previousChecksum = file(kanjidicChecksumPath).readText().trim()
        val newChecksum = getFileHash(kanjidicPath).trim()
        println(if (previousChecksum == newChecksum) "NO" else "YES")
    }
}

val createDictJsonDir: Task by tasks.creating {
    val dictJsonDir = "$buildDir/dict-json"
    extra["dictJsonDir"] = dictJsonDir
    doLast {
        mkdir(dictJsonDir)
    }
}

val jmdictConvert: Task by tasks.creating(Exec::class) {
    group = "Convert"
    description = "Convert JMdict"
    dependsOn(createDictJsonDir, tasks.getByName("uberJar"))
    val dictJsonDir: String by createDictJsonDir.extra
    val jmdictPath: String by jmdictExtract.extra
    commandLine = listOf(
        "java",
        "-Djdk.xml.entityExpansionLimit=0", // To avoid errors about # of entities in XML files
        "-jar",
        (tasks.getByName("uberJar") as Jar).archiveFile.get().asFile.path,
        "convert-jmdict",
        "--version=$version",
        "--languages=${jmdictLanguages.joinToString(",")}",
        "--report=$dictJsonDir${File.separator}$jmdictReportFile",
        jmdictPath,
        dictJsonDir,
    )
}

val jmnedictConvert: Task by tasks.creating(Exec::class) {
    group = "Convert"
    description = "Convert JMnedict"
    dependsOn(createDictJsonDir, tasks.getByName("uberJar"))
    val dictJsonDir: String by createDictJsonDir.extra
    val jmnedictPath: String by jmnedictExtract.extra
    commandLine = listOf(
        "java",
        "-Djdk.xml.entityExpansionLimit=0", // To avoid errors about # of entities in XML files
        "-jar",
        (tasks.getByName("uberJar") as Jar).archiveFile.get().asFile.path,
        "convert-jmnedict",
        "--version=$version",
        "--languages=${jmnedictLanguages.joinToString(",")}",
        "--report=$dictJsonDir${File.separator}$jmnedictReportFile",
        jmnedictPath,
        dictJsonDir,
    )
}

val kanjidicConvert: Task by tasks.creating(Exec::class) {
    group = "Convert"
    description = "Convert Kanjidic"
    dependsOn(createDictJsonDir, tasks.getByName("uberJar"))
    val dictJsonDir: String by createDictJsonDir.extra
    val kanjidicPath: String by kanjidicExtract.extra
    commandLine = listOf(
        "java",
        "-Djdk.xml.entityExpansionLimit=0", // To avoid errors about # of entities in XML files
        "-jar",
        (tasks.getByName("uberJar") as Jar).archiveFile.get().asFile.path,
        "convert-kanjidic",
        "--version=$version",
        "--languages=${kanjidicLanguages.joinToString(",")}",
        "--report=$dictJsonDir${File.separator}$kanjidicReportFile",
        kanjidicPath,
        dictJsonDir,
    )
}

val kradfileConvert by tasks.creating(Exec::class) {
    group = "Convert"
    description = "Convert KRADFILE and KRADFILE2"
    dependsOn(createDictJsonDir, tasks.getByName("uberJar"))
    val dictJsonDir: String by createDictJsonDir.extra
    val kradfilePath: String by kradfileExtract.extra
    val kradfile2Path: String by kradfileExtract.extra
    commandLine = listOf(
        "java",
        "-Djdk.xml.entityExpansionLimit=0", // To avoid errors about # of entities in XML files
        "-jar",
        (tasks.getByName("uberJar") as Jar).archiveFile.get().asFile.path,
        "convert-kradfile",
        "--version=$version",
        kradfilePath,
        kradfile2Path,
        dictJsonDir,
    )
}

val radkfileConvert by tasks.creating(Exec::class) {
    group = "Convert"
    description = "Convert RADKFILE (combined radkfilex)"
    dependsOn(createDictJsonDir, tasks.getByName("uberJar"))
    val dictJsonDir: String by createDictJsonDir.extra
    val radkfilexPath: String by kradfileExtract.extra
    commandLine = listOf(
        "java",
        "-Djdk.xml.entityExpansionLimit=0", // To avoid errors about # of entities in XML files
        "-jar",
        (tasks.getByName("uberJar") as Jar).archiveFile.get().asFile.path,
        "convert-radkfile",
        "--version=$version",
        radkfilexPath,
        dictJsonDir,
    )
}

val convert: Task by tasks.creating {
    group = "Convert"
    description = "Convert all dictionaries"
    dependsOn(jmdictConvert, jmnedictConvert, kanjidicConvert, kradfileConvert, radkfileConvert)
}

val zipAll: Task by tasks.creating {
    group = "Distribution"
    description = "Zip all JSON files"
    val dictJsonDir: String by createDictJsonDir.extra
    fileTree(dictJsonDir)
        .filter { it.isFile && it.extension == "json" }
        .forEachIndexed { idx, file ->
            dependsOn.add(tasks.create("zip$idx", Zip::class) {
                from(dictJsonDir) { include(file.name) }
                archiveFileName.set("${file.name}.zip")
            })
        }
}

val tarAll: Task by tasks.creating {
    group = "Distribution"
    description = "Tar+gzip all JSON files"
    val dictJsonDir: String by createDictJsonDir.extra
    fileTree(dictJsonDir)
        .filter { it.isFile && it.extension == "json" }
        .forEachIndexed { idx, file ->
            dependsOn.add(tasks.create("tar$idx", Tar::class) {
                compression = Compression.GZIP
                from(dictJsonDir) { include(file.name) }
                archiveFileName.set("${file.name}.tgz")
            })
        }
}

val archive: Task by tasks.creating {
    group = "Distribution"
    description = "Create archives of all JSON files"
    dependsOn(zipAll, tarAll)
}
