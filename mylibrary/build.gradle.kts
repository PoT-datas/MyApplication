import com.adarshr.gradle.testlogger.theme.ThemeType.STANDARD
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.base.DokkaBaseConfiguration

plugins {
    id("com.android.library")
    id("kotlin-android")
    id("org.jetbrains.dokka") version "1.6.21"
    id("jacoco")
    id("maven-publish")
    // To generate signature and checksum files for each artifact
    id("signing")
    // To print beautiful logs on the console while running tests with Gradle
    // Doesn't work for Android instrumented tests
    id("com.adarshr.test-logger") version "3.0.0"
}

buildscript {
    dependencies {
        // Needed to be able to configure Dokka like below
        //  pluginConfiguration<DokkaBase, DokkaBaseConfiguration> { ...
        // See https://github.com/Kotlin/dokka/issues/2513#issuecomment-1141043184
        classpath("org.jetbrains.dokka:dokka-base:1.6.21")
    }
}

// Could also have used ${rootProject.extra["kotlinVersion"]}
val kotlinVersion: String by rootProject.extra
val jacocoVersion: String by rootProject.extra
val composeVersion = "1.2.0-beta02"

group = "api.ttt.android"
version = "1.0.0"
val githubProjectName = "MyApplication"




/**
 * plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}*/

android {
    namespace = "com.ttt.mylibrary"
    compileSdk = 33

    defaultConfig {
        minSdk = 21

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.8.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}




jacoco {
    toolVersion = jacocoVersion
}

// Fixes the problem with JaCoCo in GitHub CI
// See https://youtrack.jetbrains.com/issue/KT-44757
configurations.all{
    resolutionStrategy {
        eachDependency {
            if (requested.group == "org.jacoco") {
                useVersion("0.8.8")
            }
        }
    }
}

apply(from = "${rootProject.projectDir}/scripts/configure-jacoco.gradle.kts")

/*
 * Configure the [test-logger plugin](https://github.com/radarsh/gradle-test-logger-plugin).
 * Also see [this](https://stackoverflow.com/q/3963708/)
 * and [this](https://stackoverflow.com/a/31774254/)
 */
testlogger {
    theme = STANDARD
    slowThreshold = 5000 /* ms */
    showSimpleNames = true
}

tasks.withType(Test::class) {
    // Specifies whether failing tests should fail the build
    ignoreFailures = false

    useJUnitPlatform {
        excludeEngines("junit-vintage")
    }
    configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        // https://github.com/gradle/gradle/issues/5184#issuecomment-457865951
        excludes = listOf("jdk.internal.*")
    }
}

/**
 * Uploading to a Maven repository requires sources and javadoc files as well.
 * TODO: Replace the following block with the below code;
 *  it requires Android Studio Bumblebee or IntelliJ Android plugin 2021.1
 *  and Android Gradle Plugin version 7.1.
 * ```kotlin
 * publishing {
 *   singleVariant("release") {
 *     withSourcesJar()
 *     withJavadocJar()
 *   }
 * }
 * ```
 */
/**
 * Uploading to a Maven repository requires sources and javadoc files as well.
 *
 * See [this gist](https://gist.github.com/kibotu/994c9cc65fe623b76b76fedfac74b34b) for groovy version.
 */
lateinit var sourcesArtifact: PublishArtifact
lateinit var javadocArtifact: PublishArtifact
tasks {
    val sourcesJar by creating(Jar::class) {
        archiveClassifier.set("sources")
        from(android.sourceSets["main"].java.srcDirs)
    }

    val dokkaHtml by getting(org.jetbrains.dokka.gradle.DokkaTask::class)

    val javadocJar by creating(Jar::class) {
        dependsOn(dokkaHtml)
        archiveClassifier.set("javadoc")
        from(dokkaHtml.outputDirectory)
    }

    artifacts {
        sourcesArtifact = archives(sourcesJar)
        javadocArtifact = archives(javadocJar)
    }
}

tasks.dokkaHtml.configure {
    dokkaSourceSets {
        // See the buildscript block above and also
        // https://github.com/Kotlin/dokka/issues/2406
        pluginConfiguration<DokkaBase, DokkaBaseConfiguration> {
            customAssets = listOf(
                file("docs/logo.svg"),
                file("docs/logo-icon.svg")
            )
            customStyleSheets = listOf(file("docs/logo-styles.css"))
            separateInheritedMembers = true
        }

        configureEach { // OR named("main") {
            noAndroidSdkLink.set(false)
            // The label shown for the link in the "Sources" tab of an element docs
            displayName.set("GitHub")
            sourceLink {
                // Unix based directory relative path to the root of the project (where you execute gradle respectively).
                localDirectory.set(file("src/main/kotlin"))

                // URL showing where the source code can be accessed through the web browser
                remoteUrl.set(
                    uri("https://github.com/mahozad/$githubProjectName/blob/main/${projectDir.name}/src/main/kotlin").toURL()
                )
                // Suffix which is used to append the line number to the URL. Use #L for GitHub
                remoteLineSuffix.set("#L")
            }
        }
    }
}

/**
 * Maven Publish plugin allows you to publish build artifacts to an Apache Maven repository.
 * See [here](https://docs.gradle.org/current/userguide/publishing_maven.html)
 * and [here](https://developer.android.com/studio/build/maven-publish-plugin)
 * and [here](https://maven.apache.org/repository/guide-central-repository-upload.html)
 *
 * Use *publish* task to publish the artifact to the defined repositories.
 */
afterEvaluate {
    publishing {
        repositories {
            /* The Sonatype Maven Central repository is defined in the publish.gradle script */

            // GitHub Packages repository
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/PoT-datas/$githubProjectName")
                credentials {
                    username = project.properties["github.username"] as String? ?: System.getenv("GITHUB_ACTOR") ?: ""
                    password = project.properties["github.token"] as String? ?: System.getenv("GITHUB_TOKEN") ?: ""
                }
            }

            // Local repository which can be published to first to check artifacts
            maven {
                name = "LocalTestRepo"
                url = uri("file://${buildDir}/local-repository")
            }
        }
        publications {
            // Creates a Maven publication called "release".
            create<MavenPublication>("Release") {
                // Applies the component for the release build variant (two artifacts: the aar and the sources)
                from(components["release"])
                // You can then customize attributes of the publication as shown below
                groupId = "api.ttt.android"
                artifactId = "my-application"
                version = project.version.toString()
                artifact(sourcesArtifact)
                artifact(javadocArtifact)
                pom {
                    url.set("https://ttt.api/$githubProjectName")
                    name.set(githubProjectName)
                    description.set(
                        """
                        A library for creating pie charts and donut charts in Android.
                        The aim of this library is to provide a full-featured chart view and to enable users to customize it to the most extent possible.
                        Visit the project on GitHub to learn more.
                        """.trimIndent()
                    )
                    inceptionYear.set("2021")
                    licenses {
                        license {
                            name.set("Apache-2.0 License")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    developers {
                        developer {
                            id.set("ttt")
                            name.set("Olivier Tambo")
                            url.set("https://ttt.api/")
                            email.set("")
                            roles.set(listOf("Lead Developer"))
                            timezone.set("GMT+4:30")
                        }
                    }
                    contributors {
                        // contributor {}
                    }
                    scm {
                        tag.set("HEAD")
                        url.set("https://github.com/mahozad/$githubProjectName")
                        connection.set("scm:git:github.com/mahozad/$githubProjectName.git")
                        developerConnection.set("scm:git:ssh://github.com/mahozad/$githubProjectName.git")
                    }
                    issueManagement {
                        system.set("GitHub")
                        url.set("https://github.com/mahozad/$githubProjectName/issues")
                    }
                    ciManagement {
                        system.set("GitHub Actions")
                        url.set("https://github.com/mahozad/$githubProjectName/actions")
                    }
                }
            }
            create<MavenPublication>("Debug") {
                from(components["debug"])
                groupId = "ir.mahozad.android"
                artifactId = "pie-chart-debug"
                version = project.version.toString()
            }
        }
    }
}

// Usage: gradlew incrementVersion [-P[mode=major|minor|patch]|[overrideVersion=x]]
tasks.create("incrementVersion") {
    group = "versioning"
    description = "Increments the library version everywhere it is used."
    doLast {
        val (oldMajor, oldMinor, oldPatch) = version.toString().split(".")
        var (newMajor, newMinor, newPatch) = arrayOf(oldMajor, oldMinor, "0")
        when (properties["mode"]) {
            "major" -> newMajor = (oldMajor.toInt() + 1).toString().also { newMinor = "0" }
            "minor" -> newMinor = (oldMinor.toInt() + 1).toString()
            else    -> newPatch = (oldPatch.toInt() + 1).toString()
        }
        var newVersion = "$newMajor.$newMinor.$newPatch"
        properties["overrideVersion"]?.toString()?.let { newVersion = it }
        with(file("../README.md")) {
            writeText(
                readText()
                    .replaceFirst(":$version", ":$newVersion"))
        }
    }
}

// Used by a GitHub Action. See .github/ directory.
// Could also use the following command and then parse it:
//  ./gradlew :piechart:dependencyInsight --configuration kotlinCompilerClasspath --dependency org.jetbrains.kotlin:kotlin-stdlib
tasks.register("printKotlinVersion") {
    group = "Custom"
    description = "Prints the version of Kotlin stdlib used in the project."
    doLast {
        val version = configurations
            .getByName("implementation")
            .dependencies
            .first { it.name == "kotlin-stdlib" }
            .version
        println(version)
        println("::set-output name=kotlinVersion::$version")
    }
}

// val PUBLISH_GROUP_ID by extra("ir.mahozad.android")
// val PUBLISH_ARTIFACT_ID by extra("pie-chart")
// val PUBLISH_VERSION by extra("0.1.0")

apply(from = "${rootProject.projectDir}/scripts/publish-module.gradle")