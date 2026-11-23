package com.example.ui.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** Finds the hosting Activity from a Compose LocalContext, including wrapped contexts. */
fun Context.findActivity(): Activity? {
    var current: Context = this
    val visited = HashSet<Context>()
    while (visited.add(current)) {
        when (current) {
            is Activity -> return current
            is ContextWrapper -> current = current.baseContext
            else -> return null
        }
    }
    return null
}
