package android.os
interface IBinder
object Binder { var callerUid: Int = 1000; fun getCallingUid() = callerUid }
object Process { const val SYSTEM_UID: Int = 1000 }
