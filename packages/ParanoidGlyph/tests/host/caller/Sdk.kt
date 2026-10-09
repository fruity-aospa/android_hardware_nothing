package com.nothing.thirdparty
import android.os.IBinder
interface IGlyphService {
fun setFrameColors(levels: IntArray?); fun openSession(); fun closeSession(); fun register(name: String): Boolean; fun registerSDK(name: String,key: String): Boolean; fun registerMatrixSDK(name:String): Boolean; fun setMatrixColors(levels:IntArray?); fun setGlyphMatrixTimeout(active:Boolean); fun setAppMatrixColors(levels:IntArray?); fun closeAppMatrix()
abstract class Stub: IGlyphService, IBinder
}
