package com.example.face_recognition

import android.content.Context
import com.example.face_recognition.KnownPerson
import com.example.face_recognition.SerializableKnownPerson
import com.example.face_recognition.toSerializable
import com.google.gson.GsonBuilder
import java.io.File
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

fun saveKnownPersonsToJson(context: Context, persons: List<KnownPerson>) {
    try {
        val file = File(context.filesDir, "known_faces.json")
        val gson = GsonBuilder().setPrettyPrinting().create()

        val serializableList = persons.map { it.toSerializable() }
        val json = gson.toJson(serializableList)

        file.writeText(json)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}



fun loadKnownPersonsFromJson(context: Context): List<KnownPerson> {
    return try {
        val file = File(context.filesDir, "known_faces.json")
        if (!file.exists()) return emptyList()

        val json = file.readText()
        val gson = Gson()

        val type = object : TypeToken<List<SerializableKnownPerson>>() {}.type
        val serializableList: List<SerializableKnownPerson> = gson.fromJson(json, type)

        serializableList.map { it.toModel() }
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}
