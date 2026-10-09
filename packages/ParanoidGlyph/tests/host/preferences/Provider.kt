package android.provider
import android.content.ContentResolver
object Settings { object Secure { fun getString(resolver:ContentResolver,key:String):String?=resolver.secure[key] } }
