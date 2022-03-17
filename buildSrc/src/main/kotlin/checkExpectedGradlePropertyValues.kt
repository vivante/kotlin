/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

import org.gradle.api.Project

/**
 * Mechanism to warn developers when a given Gradle property does not match the developer's expectation.
 *
 * There may be some Gradle properties, that are defined in the project and will change over time (e.g. defaultSnapshotVersion).
 * Some developers (and QA) will need to be very clear about the value of this property.
 *
 * In order to get notified about the value of the property changing, it is possible to define the same property in
 * ~/.gradle/gradle.properties with a given `.expect` suffix to ensure the value.
 *
 * e.g. if a developer set's
 *
 * `defaultSnapshotVersion.expect=1.6.255-SNAPSHOT` and the value gets bumped to `1.7.255-SNAPSHOT` after pulling from master,
 * the developer will notice this during project configuration phase.
 */
fun Project.checkExpectedGradlePropertyValues() {
    data class UnexpectedPropertyValue(
        val key: String, val expectedValue: String, val actualValue: String
    )

    val expectSuffix = ".expect"
    val expectKeys = properties.keys.filter { it.endsWith(expectSuffix) }

    val unexpectedPropertyValues = expectKeys.mapNotNull { expectKey ->
        val actualKey = expectKey.removeSuffix(expectSuffix)
        if (!properties.containsKey(actualKey)) return@mapNotNull null
        val expectValue = properties[expectKey]?.toString() ?: return@mapNotNull null
        val actualValue = properties[actualKey].toString()
        if (expectValue != actualValue) UnexpectedPropertyValue(
            actualKey, expectedValue = expectValue, actualValue = actualValue
        ) else null
    }.distinct()

    if (unexpectedPropertyValues.isEmpty()) {
        return
    }

    throw IllegalArgumentException(
        buildString {
            appendLine("Unexpected Gradle property values found in ${project.displayName}:")
            unexpectedPropertyValues.forEach { value ->
                appendLine("Expected ${value.key} to be '${value.expectedValue}', but found '${value.actualValue}'")
            }
        }
    )
}
