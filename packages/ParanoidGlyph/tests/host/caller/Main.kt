import android.os.Binder
import co.aospa.glyph.Services.ThirdPartyService
import co.aospa.glyph.util.instance
import com.nothing.thirdparty.IGlyphService
fun main() {
val service = ThirdPartyService(); val binder = service.onBind(null) as IGlyphService
val methods: List<() -> Unit> = listOf({binder.setFrameColors(IntArray(36){255})},{binder.openSession()},{binder.closeSession()},{binder.register("app")},{binder.registerSDK("app","key")},{binder.registerMatrixSDK("app")},{binder.setMatrixColors(IntArray(625))},{binder.setGlyphMatrixTimeout(true)},{binder.setAppMatrixColors(IntArray(625))},{binder.closeAppMatrix()})
for (uid in listOf(0,1001,10001,99000)) { Binder.callerUid=uid; for (method in methods) { var rejected=false; try {method()} catch(e: SecurityException) {rejected=true}; check(rejected) {"Accepted untrusted UID $uid"} } }
check(instance.animator.frames==0); check(instance.animator.ended==0)
Binder.callerUid=1000; binder.setFrameColors(IntArray(36){255}); check(instance.animator.frames==1)
instance.preferences.isEnabled=false; binder.setFrameColors(IntArray(36){255}); check(instance.animator.frames==1)
check(!binder.registerMatrixSDK("app")); binder.closeSession(); check(instance.animator.ended==1)
service.onUnbind(null); check(instance.animator.ended==2); service.onDestroy(); check(instance.animator.ended==3)
println("PASS: all10 binder methods reject four untrusted UID classes; trusted adapter/disabled Glyph/matrix unsupported/session and owner cleanup")
}
