package com.example.aiassistant

import android.content.Context
import android.os.Environment
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class FileDataProcessor(private val context: Context) {

    fun readTextFile(relativeFileName: String): String {
        return try {
            val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), relativeFileName)
            if (file.exists()) file.readText() else "File '$relativeFileName' not found in Documents."
        } catch (e: Exception) {
            "Error reading file: ${e.message}"
        }
    }

    fun writeTextFile(relativeFileName: String, content: String): String {
        return try {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, relativeFileName)
            file.writeText(content)
            "Successfully saved to Documents/$relativeFileName"
        } catch (e: Exception) {
            "File write failed: ${e.message}"
        }
    }

    fun listDocumentsFiles(): String {
        return try {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val files = dir.listFiles()?.map { it.name } ?: emptyList()
            if (files.isEmpty()) "Documents directory is empty." else files.joinToString(", ")
        } catch (e: Exception) {
            "Error listing directory: ${e.message}"
        }
    }

    fun parseCsvToJson(relativeFileName: String): String {
        return try {
            val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), relativeFileName)
            if (!file.exists()) return "File not found."

            val lines = file.readLines()
            if (lines.isEmpty()) return "CSV is empty."

            val headers = lines[0].split(",").map { it.trim() }
            val jsonArray = JSONArray()

            for (i in 1 until lines.size) {
                val values = lines[i].split(",")
                val rowObj = JSONObject()
                for (j in headers.indices) {
                    val value = if (j < values.size) values[j].trim() else ""
                    rowObj.put(headers[j], value)
                }
                jsonArray.put(rowObj)
            }
            jsonArray.toString()
        } catch (e: Exception) {
            "CSV parsing error: ${e.message}"
        }
    }
}
