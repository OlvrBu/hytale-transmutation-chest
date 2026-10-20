plugins {
// Uncomment if you are using IntelliJ.
//  idea
    java
    id("com.azuredoom.hytale-tools") version "1.+"
}


tasks.withType<Javadoc>().configureEach {
    (options as org.gradle.external.javadoc.StandardJavadocDocletOptions).addStringOption("Xdoclint:-missing", "-quiet")
}

group = project.property("group").toString()

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(property("java_version").toString().toInt()))
}

hytaleTools {
    javaVersion = property("java_version").toString().toInt()
    hytaleVersion = property("hytale_version").toString()
    manifestServerVersion = property("manifestServerVersion").toString()
    manifestGroup = property("manifest_group").toString()
    modId = property("mod_id").toString()
    modDescription = property("mod_description").toString()
    modUrl = property("mod_url").toString()
    mainClass = property("main_class").toString()
    modCredits = property("mod_author").toString()
    manifestDependencies = property("manifest_dependencies").toString()
    manifestOptionalDependencies = property("manifest_opt_dependencies").toString()
    curseforgeId = property("curseforgeID").toString()
    disabledByDefault = property("disabled_by_default").toString().toBoolean()
    includesPack = property("includes_pack").toString().toBoolean()
    patchline = property("patchline").toString()
    injectServerJavadocsIntoSources = property("injectServerJavadocsIntoSources").toString().toBoolean()
    generateAssetsBinary = property("generateAssetsBinary").toString().toBoolean()
    // hytaleHomeOverride = property("hytaleHomeOverride").toString()

    // Dev server only (runServer): "--allow-op" enables /op self so you can grant yourself admin.
    // Never use this flag on a public server.
    // (The retail client refuses "--auth-mode insecure", so the dev server must log in via /auth login device.)
    serverArgs.addAll("--allow-op")
    // The plugin's default -Xms4G -Xmx4G got the dev server OOM-killed in the 7.6 GB sandbox while
    // Gradle was also running. 2.5 GB is plenty for a small dev world; later -X flags win.
    serverJvmArgs.addAll("-Xms1G", "-Xmx2560m")
}

repositories {
    mavenCentral()
}

// Tests run without a server, but the config parser uses the Gson bundled in the server jar,
// so put the compile-only server dependency on the test classpath too.
configurations.testImplementation {
    extendsFrom(configurations.compileOnly.get())
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.named<Jar>("jar") {
    archiveBaseName.set(project.property("mod_id").toString())
    archiveVersion.set(project.property("version").toString())
}

// Uncomment if you are using IntelliJ.
// idea {
//     module {
//         isDownloadSources = true
//         isDownloadJavadoc = true
//     }
// }
