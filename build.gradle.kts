import groovy.lang.GroovySystem
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.api.Git
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.openstreetmap.josm.gradle.plugin.GitDescriber
import org.openstreetmap.josm.gradle.plugin.logCoverage
import org.openstreetmap.josm.gradle.plugin.logSkippedTasks
import org.openstreetmap.josm.gradle.plugin.logTaskDuration

import java.time.Duration
import java.time.Instant
import java.net.URL
import java.util.Locale

buildscript {
  repositories {
    jcenter()
  }
  dependencies {
    val kotlinVersion: String by project.project(":buildSrc").extra
    classpath(kotlin("gradle-plugin", kotlinVersion))
  }
}

plugins {
  id("com.gradle.plugin-publish").version("0.10.0")
  id("com.github.ben-manes.versions").version("0.20.0")
  id("org.jetbrains.dokka").version("0.9.17")

  jacoco
  maven
  eclipse
  `java-gradle-plugin`
  `maven-publish`
}

apply(plugin = "kotlin")

logSkippedTasks()
gradle.taskGraph.logTaskDuration()

afterEvaluate {
  // use Javadoc generated by Dokka
  tasks["publishPluginJavaDocsJar"].dependsOn.remove(tasks["javadoc"])
  tasks.withType(Jar::class.java)["publishPluginJavaDocsJar"].from(tasks["dokka"])
}

repositories {
  jcenter()
}

// Reuse the kotlin sources from the "buildSrc" project
gradle.projectsEvaluated {
  // the following line is here so IntelliJ correctly picks up the dependency on project :buildSrc
  sourceSets["main"].compileClasspath += project(":buildSrc").sourceSets["main"].output
  sourceSets["main"].withConvention(KotlinSourceSet::class) {
    project(":buildSrc").sourceSets["main"]
      .withConvention(KotlinSourceSet::class) { kotlin.srcDirs }
      .forEach {
        this.kotlin.srcDir(it)
      }
  }
  project(":buildSrc").configurations["implementation"].dependencies.forEach {
    // Add all `implementation` dependencies of the `buildSrc` project
    dependencies.implementation(it)
  }
}

dependencies {
  val junitVersion = "5.3.1"
  val kotlinVersion: String by project.project(":buildSrc").extra
  val jacksonVersion = "2.9.7"

  implementation(localGroovy())
  implementation(kotlin("stdlib-jdk8", kotlinVersion))
  implementation("com.squareup.okhttp3", "okhttp", "3.11.0")
  implementation("com.beust","klaxon", "3.0.8").because("versions 3.0.9 and 3.0.10 are broken, see https://github.com/cbeust/klaxon/issues/202")
  implementation("com.fasterxml.jackson.module", "jackson-module-kotlin", jacksonVersion)
  implementation("com.fasterxml.jackson.dataformat", "jackson-dataformat-yaml", jacksonVersion)
  implementation("com.vladsch.flexmark:flexmark:0.34.48")
  testImplementation("org.junit.jupiter", "junit-jupiter-api", junitVersion)
  testImplementation("com.github.tomakehurst","wiremock","2.19.0")
  testImplementation("ru.lanwen.wiremock", "wiremock-junit5", "1.2.0")
  testRuntimeOnly("org.junit.jupiter", "junit-jupiter-engine", junitVersion)
  testImplementation(kotlin("reflect", kotlinVersion))
}

jacoco {
  toolVersion = "0.8.2"
}
tasks.withType(JacocoReport::class.java) {
  this.logCoverage()
}

tasks.withType(Test::class.java) {
  useJUnitPlatform()
  finalizedBy(tasks.getByName("jacocoTestReport"))
}

tasks {
  "dokka"(DokkaTask::class) {
    outputFormat = "html"
    outputDirectory = "$buildDir/docs/kdoc"
    gradle.projectsEvaluated {
      project(":buildSrc").sourceSets["main"].withConvention(KotlinSourceSet::class) {
        kotlin.srcDirs.forEach {
          sourceDirs = sourceDirs.toList().plus(it).asIterable()
        }
      }
    }
  }
  withType(DokkaTask::class.java) {
    includes = listOfNotNull("src/main/kotlin/packages.md")
    jdkVersion = 8
    skipEmptyPackages = false

    externalDocumentationLinks.add(DokkaConfiguration.ExternalDocumentationLink.Builder(URL("https://docs.gradle.org/${project.gradle.gradleVersion}/javadoc/")).build())
    externalDocumentationLinks.add(DokkaConfiguration.ExternalDocumentationLink.Builder(URL("http://docs.groovy-lang.org/${GroovySystem.getVersion()}/html/api/")).build())
  }
}

group = "org.openstreetmap.josm"

val tmpVersion = GitDescriber(projectDir).describe()
version = if (tmpVersion[0] == 'v') tmpVersion.substring(1) else tmpVersion

// for the plugin-publish (publish to plugins.gradle.org)
pluginBundle {
  website = "https://gitlab.com/floscher/gradle-josm-plugin#readme"
  vcsUrl = "https://gitlab.com/floscher/gradle-josm-plugin.git"
  description = "This plugin helps with developing for the JOSM project."
  tags = listOf("josm", "openstreetmap", "osm")

  plugins.create("josmPlugin") {
    id = project.group.toString()
    displayName = "Gradle JOSM plugin"
  }
}
// for the java-gradle-plugin (local publishing)
gradlePlugin {
  plugins.create("josmPlugin") {
    id = project.group.toString()
    implementationClass = "org.openstreetmap.josm.gradle.plugin.JosmPlugin"
  }
}

val buildDirRepo = publishing.repositories.maven("$buildDir/maven") {
  name = "buildDir"
}

val awsAccessKeyId: String? = System.getenv("AWS_ACCESS_KEY_ID")
val awsSecretAccessKey: String? = System.getenv("AWS_SECRET_ACCESS_KEY")
val s3Repo = if (awsAccessKeyId == null || awsSecretAccessKey == null) {
  logger.lifecycle(
    "Note: Set the environment variables AWS_ACCESS_KEY_ID ({} set) and AWS_SECRET_ACCESS_KEY ({} set) to be able to publish the plugin to s3://gradle-josm-plugin .",
    if (awsAccessKeyId == null) { "not" } else { "is" },
    if (awsSecretAccessKey == null) { "not" } else { "is" }
  )
  null
} else {
  publishing.repositories.maven {
    name = "s3"
    setUrl("s3://gradle-josm-plugin")
    credentials(AwsCredentials::class.java) {
      setAccessKey(System.getenv("AWS_ACCESS_KEY_ID"))
      setSecretKey(System.getenv("AWS_SECRET_ACCESS_KEY"))
    }
  }
}

project.afterEvaluate {
  tasks.withType(PublishToMavenRepository::class).configureEach {
    if (repository == buildDirRepo) {
      description = "Deploys the gradle-josm-plugin to a local Maven repository at ${repository.url}"
      tasks.withType(Test::class).forEach { it.dependsOn(this) }
    } else if (repository == s3Repo) {
      description = "Deploys the gradle-josm-plugin to a Maven repository on AWS S3: ${repository.url}"
    }
    doLast {
      logger.lifecycle("Version ${project.version} is now deployed to ${repository.url}")
    }
  }
}

eclipse.project {
  natures(
    "org.eclipse.buildship.core.gradleprojectnature",
    "org.eclipse.jdt.core.javanature",
    "org.jetbrains.kotlin.core.kotlinNature"
  )
  buildCommand("org.eclipse.buildship.core.gradleprojectbuilder")
  buildCommand("org.eclipse.jdt.core.javabuilder")
  buildCommand("org.jetbrains.kotlin.ui.kotlinBuilder")
}
