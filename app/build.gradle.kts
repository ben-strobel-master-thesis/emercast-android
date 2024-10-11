import com.google.protobuf.gradle.proto
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    id("com.google.gms.google-services")
    id("org.openapi.generator") version "6.6.0"
    id("com.google.protobuf")
}

android {
    namespace = "com.strobel.emercast"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.strobel.emercast"
        minSdk = 33
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
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
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    sourceSets["main"].java {
        srcDir("$rootDir/generated/src/main/java")
    }
    sourceSets["main"].proto {
        srcDir("$rootDir/api-specs")
    }
    tasks.preBuild {
        dependsOn(tasks.withType<GenerateTask>())
    }
}

openApiGenerate {
    generatorName.set("kotlin")
    outputDir.set("$rootDir/generated")
    inputSpec.set("$rootDir/api-specs/api-spec.yml")
    apiPackage.set("com.openapi.gen.android.api")
    modelPackage.set("com.openapi.gen.android.dto")
    library.set("jvm-volley")
    additionalProperties.set(mapOf(
        "serializationLibrary" to "gson"
    ))
    typeMappings.set(mapOf(
        "string+ObjectId" to "UUID"
    ))
}

protobuf {
    protoc { artifact = "com.google.protobuf:protoc:3.25.4" }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
               create("java") {
                   option("lite")
               }
            }
        }
    }
    // files("$rootDir/api specs")
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.messaging)
    implementation(libs.volley)
    implementation(libs.gson)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.protobuf)
    implementation(libs.play.services.location)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}