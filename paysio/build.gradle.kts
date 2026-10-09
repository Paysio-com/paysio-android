plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("maven-publish")
    id("signing")
}

android {
    namespace = "com.paysio.sdk"
    // VGS Collect 2.1.0 is compiled against SDK 37; stay at or above it so its AAR
    // metadata (minCompileSdk) does not reject the build.
    compileSdk = 37

    defaultConfig {
        minSdk = 23
        consumerProguardFiles("proguard-rules.pro")
        buildConfigField("String", "VERSION_NAME", "\"${project.properties["VERSION_NAME"]}\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // VGS Collect Android SDK 2.1.0: Compose widgets in
    // com.verygoodsecurity.vgscollect.widget.compose.material3 (and .material).
    // Its Compose dependencies are compileOnly, so the host (this library) provides them.
    api("com.verygoodsecurity:vgscollect:2.1.0")

    // The Compose BOM MUST stay at or above the Compose that VGS Collect was built with.
    // VGS declares Compose as compileOnly, so whatever this BOM resolves is what its
    // widgets run against; an older Compose crashes at runtime with NoSuchMethodError
    // (seen with BOM 2024.12.01: RememberSaveableKt.rememberSaveable in
    // rememberVgsCardNumberTextFieldState). Keep the example app's BOM identical.
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    api(composeBom)
    api("androidx.compose.ui:ui")
    api("androidx.compose.foundation:foundation")
    api("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.13.0")
    // core-ktx, appcompat and material at (or above) the versions VGS Collect 2.1.0 pulls.
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.14.0")

    // VGS Collect 2.1.0 depends on OkHttp 5.5.0, which wins resolution anyway.
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
}

// ---------------------------------------------------------------------------
// Maven Central (Sonatype Central Portal). Namespace com.paysio must be verified
// once (DNS TXT on paysio.com). Credentials come from CI env / ~/.gradle/gradle.properties.
// ---------------------------------------------------------------------------
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = project.property("GROUP") as String
                artifactId = project.property("POM_ARTIFACT_ID") as String
                version = project.property("VERSION_NAME") as String

                pom {
                    name.set("Paysio Mobile SDK for Android")
                    description.set("Card and bank entry, 3-D Secure and checkout for Paysio merchants' Android apps.")
                    url.set("https://paysio.com/developers")
                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://opensource.org/licenses/MIT")
                        }
                    }
                    developers {
                        developer {
                            id.set("paysio")
                            name.set("Paysio")
                            email.set("support@paysio.com")
                        }
                    }
                    scm {
                        connection.set("scm:git:https://github.com/Paysio-com/paysio-android.git")
                        developerConnection.set("scm:git:ssh://git@github.com/Paysio-com/paysio-android.git")
                        url.set("https://github.com/Paysio-com/paysio-android")
                    }
                }
            }
        }
        repositories {
            maven {
                name = "central"
                // Central Portal OSSRH-compatible staging endpoint.
                url = uri("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/")
                credentials {
                    username = (project.findProperty("centralUsername") ?: System.getenv("CENTRAL_USERNAME") ?: "") as String
                    password = (project.findProperty("centralPassword") ?: System.getenv("CENTRAL_PASSWORD") ?: "") as String
                }
            }
        }
    }

    signing {
        val keyId = project.findProperty("signing.keyId") as String?
        val hasKey = !keyId.isNullOrBlank()
        isRequired = hasKey && gradle.taskGraph.hasTask("publish")
        if (hasKey) {
            sign(publishing.publications["release"])
        }
    }
}
