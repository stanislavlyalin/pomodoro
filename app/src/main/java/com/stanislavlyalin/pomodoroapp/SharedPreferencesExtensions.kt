package com.stanislavlyalin.pomodoroapp

import android.content.SharedPreferences

inline fun SharedPreferences.withPrefs(editorAction: (SharedPreferences.Editor) -> Unit) {
    val editor = edit()
    editorAction(editor)
    editor.apply()
}
