package com.alarm.qinghao.wakeup.data

import androidx.room.TypeConverter

class Converters {

    @TypeConverter
    fun packagesToString(packages: List<String>?): String =
        packages.orEmpty().filter { it.isNotBlank() }.joinToString("\n")

    @TypeConverter
    fun stringToPackages(raw: String?): List<String> =
        if (raw.isNullOrBlank()) emptyList() else raw.split("\n").filter { it.isNotBlank() }
}
