package android.content
class ContentResolver { val secure=mutableMapOf<String,String>() }
class Context(val prefs:SharedPreferences) { val contentResolver=ContentResolver(); fun getString(id:Int)="default" }
class SharedPreferences(val values:MutableMap<String,Any?> = mutableMapOf()) {
fun interface OnSharedPreferenceChangeListener { fun onSharedPreferenceChanged(p:SharedPreferences,key:String?) }
fun registerOnSharedPreferenceChangeListener(l:OnSharedPreferenceChangeListener) {}; fun unregisterOnSharedPreferenceChangeListener(l:OnSharedPreferenceChangeListener) {}
fun getBoolean(k:String,d:Boolean)=values[k] as? Boolean ?: d
fun getInt(k:String,d:Int)=values[k] as? Int ?: d
fun getString(k:String,d:String?):String?=values[k] as? String ?: d
@Suppress("UNCHECKED_CAST") fun getStringSet(k:String,d:Set<String>?):Set<String>?=values[k] as? Set<String> ?: d
fun contains(k:String)=values.containsKey(k)
class Editor(val p:SharedPreferences) { fun putBoolean(k:String,v:Boolean) {p.values[k]=v}; fun putInt(k:String,v:Int) {p.values[k]=v}; fun putString(k:String,v:String) {p.values[k]=v}; fun putStringSet(k:String,v:Set<String>) {p.values[k]=v} }
}
