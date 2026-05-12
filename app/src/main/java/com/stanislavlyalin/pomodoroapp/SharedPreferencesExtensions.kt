package com.stanislavlyalin.pomodoroapp

import android.content.SharedPreferences

inline fun SharedPreferences.withPrefs(editorAction: (SharedPreferences.Editor) -> Unit) {
    val editor = edit()
    editorAction(editor)
    editor.apply()
}

inline fun SharedPreferences.withCommittedPrefs(editorAction: (SharedPreferences.Editor) -> Unit): Boolean {
    val editor = edit()
    editorAction(editor)
    return editor.commit()
}
