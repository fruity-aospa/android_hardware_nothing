package android.app
import android.content.Context
import android.content.Intent
import android.os.IBinder
open class Service: Context() { open fun onBind(intent: Intent?): IBinder? = null; open fun onUnbind(intent: Intent?): Boolean = false; open fun onDestroy() {} }
