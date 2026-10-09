package co.aospa.glyph.util
import android.content.Context
import co.aospa.glyph.system.Glyph
val instance = Glyph()
val Context.glyph: Glyph get() = instance
object GlyphLog { fun d(tag: String,message: ()->String) {} }
