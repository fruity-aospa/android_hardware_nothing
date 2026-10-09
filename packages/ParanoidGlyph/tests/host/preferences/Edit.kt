package androidx.core.content
import android.content.SharedPreferences
fun SharedPreferences.edit(action:SharedPreferences.Editor.()->Unit) {SharedPreferences.Editor(this).action()}
