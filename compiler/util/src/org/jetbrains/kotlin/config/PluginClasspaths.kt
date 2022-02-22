/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.config

import java.io.File
import java.security.DigestInputStream
import java.security.MessageDigest

class PluginClasspaths {
    companion object {
        fun serialize(classpaths: Array<String>?) = classpaths?.mapNotNull { it ->
            val jar = File(it).takeIf { it.exists() } ?: return@mapNotNull null
            val jarPath = jar.absolutePath
            val jarHash = jar.sha256()
            "$jarPath-$jarHash"
        }?.joinToString(":") ?: ""

        fun deserialize(str: String): Array<String> =
            str.split(":")
                .map {it.substringBeforeLast("-")}
                .filter(String::isNotBlank).toTypedArray()

        fun equals(old: String, new: String): Boolean {
            val oldPluginClasspath = deserializeWithHashes(old)
            val newPluginClasspath = deserializeWithHashes(new)

            // compare names of existing jars and their order
            val oldJarNames = oldPluginClasspath.keys.map { File(it).name }.toTypedArray()
            val newJarNames = newPluginClasspath.keys.map { File(it).name }.toTypedArray()
            if (!oldJarNames.contentEquals(newJarNames)) return false

            // compare hashes
            for (jar: String in oldPluginClasspath.keys) {
                if (oldPluginClasspath[jar] != newPluginClasspath[jar]) return false
            }

            return true
        }

        private fun deserializeWithHashes(str: String): Map<String, String> =
            str.split(":")
                .filter(String::isNotBlank)
                .associate { it.substringBeforeLast("-") to it.substringAfterLast("-") }

        private fun File.sha256(): String {
            val digest = MessageDigest.getInstance("SHA-256")
            DigestInputStream(this.inputStream(), digest).use { dis ->
                val buffer = ByteArray(8192)
                // Read all bytes:
                while (dis.read(buffer, 0, buffer.size) != -1) {
                }
            }
            // Convert to hex:
            return digest.digest().joinToString("") {
                Integer.toHexString((it.toInt() and 0xff) + 0x100).substring(1)
            }
        }
    }
}