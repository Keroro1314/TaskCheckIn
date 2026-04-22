package com.taskcheckin.util

import java.text.SimpleDateFormat
import java.util.*

object DateUtils {
    private val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

    fun formatTimestamp(timestamp: Long): String {
        return dateFormat.format(Date(timestamp))
    }
}