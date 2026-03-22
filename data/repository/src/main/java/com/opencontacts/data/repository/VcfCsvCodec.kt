package com.opencontacts.data.repository

import com.opencontacts.core.model.ContactSummary
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VcfHandler @Inject constructor() {
    fun parse(stream: InputStream): List<ContactSummary> {
        val contacts = mutableListOf<ContactSummary>()
        val unfolded = mutableListOf<String>()
        var previous: String? = null

        stream.bufferedReader().forEachLine { raw ->
            val line = raw.trimEnd()
            if ((line.startsWith(" ") || line.startsWith("\t")) && previous != null) {
                previous += line.trimStart()
                unfolded[unfolded.lastIndex] = previous!!
            } else {
                previous = line
                unfolded += line
            }
        }

        var name: String? = null
        var phone: String? = null
        var tags: List<String> = emptyList()
        var folder: String? = null

        fun commit() {
            val displayName = name?.trim().orEmpty()
            if (displayName.isNotBlank()) {
                contacts += ContactSummary(
                    id = UUID.randomUUID().toString(),
                    displayName = decodeVcf(displayName),
                    primaryPhone = phone?.trim()?.takeIf { it.isNotBlank() },
                    tags = tags,
                    isFavorite = false,
                    folderName = folder?.trim()?.takeIf { it.isNotBlank() },
                )
            }
        }

        unfolded.forEach { raw ->
            val line = raw.trim()
            when {
                line.equals("BEGIN:VCARD", ignoreCase = true) -> {
                    name = null
                    phone = null
                    tags = emptyList()
                    folder = null
                }
                line.startsWith("FN:", ignoreCase = true) -> name = line.substringAfter(':')
                line.startsWith("TEL", ignoreCase = true) -> {
                    phone = line.substringAfter(':').replace("[\\s\\-()]".toRegex(), "")
                }
                line.startsWith("CATEGORIES:", ignoreCase = true) -> {
                    tags = line.substringAfter(':')
                        .split(',')
                        .map { decodeVcf(it).trim() }
                        .filter { it.isNotBlank() }
                }
                line.startsWith("ORG:", ignoreCase = true) -> {
                    folder = decodeVcf(line.substringAfter(':'))
                }
                line.equals("END:VCARD", ignoreCase = true) -> commit()
            }
        }
        return contacts
    }

    fun write(contacts: List<ContactSummary>, stream: OutputStream) {
        val writer = stream.bufferedWriter()
        contacts.forEach { contact ->
            writer.write("BEGIN:VCARD\r\n")
            writer.write("VERSION:3.0\r\n")
            writer.write("FN:${encodeVcf(contact.displayName)}\r\n")
            writer.write("N:${encodeVcf(contact.displayName)};;;;\r\n")
            contact.primaryPhone?.takeIf { it.isNotBlank() }?.let {
                writer.write("TEL;TYPE=CELL:${encodeVcf(it)}\r\n")
            }
            if (contact.tags.isNotEmpty()) {
                writer.write(
                    "CATEGORIES:${contact.tags.joinToString(",") { tag -> encodeVcf(tag) }}\r\n"
                )
            }
            contact.folderName?.takeIf { it.isNotBlank() }?.let {
                writer.write("ORG:${encodeVcf(it)}\r\n")
            }
            writer.write("END:VCARD\r\n")
        }
        writer.flush()
    }

    private fun decodeVcf(value: String): String = value
        .replace("\\n", "\n")
        .replace("\\,", ",")
        .replace("\\;", ";")
        .replace("\\\\", "\\")

    private fun encodeVcf(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\n", "\\n")
        .replace(",", "\\,")
        .replace(";", "\\;")
}

@Singleton
class CsvHandler @Inject constructor() {
    private val header = "displayName,primaryPhone,tags,folderName,isFavorite"

    fun parse(stream: InputStream): List<ContactSummary> {
        val lines = stream.bufferedReader().readLines()
        if (lines.isEmpty()) return emptyList()
        val body = if (lines.first().contains("displayName", ignoreCase = true)) lines.drop(1) else lines
        return body.mapNotNull { parseLine(it.trimStart('﻿')) }
    }

    fun write(contacts: List<ContactSummary>, stream: OutputStream) {
        val writer = stream.bufferedWriter()
        writer.write(header)
        writer.newLine()
        contacts.forEach { c ->
            val row = listOf(
                c.displayName,
                c.primaryPhone.orEmpty(),
                c.tags.joinToString("|"),
                c.folderName.orEmpty(),
                c.isFavorite.toString(),
            ).joinToString(",") { cell -> quote(cell) }
            writer.write(row)
            writer.newLine()
        }
        writer.flush()
    }

    private fun parseLine(line: String): ContactSummary? {
        if (line.isBlank()) return null
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        val delimiter = if (line.count { it == ';' } > line.count { it == ',' }) ';' else ','
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == delimiter && !inQuotes -> {
                    cells += current.toString()
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        cells += current.toString()
        return ContactSummary(
            id = UUID.randomUUID().toString(),
            displayName = cells.getOrNull(0).orEmpty(),
            primaryPhone = cells.getOrNull(1)?.ifBlank { null },
            tags = cells.getOrNull(2).orEmpty().split('|').filter { it.isNotBlank() },
            isFavorite = cells.getOrNull(4).orEmpty().toBoolean(),
            folderName = cells.getOrNull(3)?.ifBlank { null },
        ).takeIf { it.displayName.isNotBlank() }
    }

    private fun quote(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""
}
