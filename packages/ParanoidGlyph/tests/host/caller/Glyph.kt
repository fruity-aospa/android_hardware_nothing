package co.aospa.glyph.system
enum class GlyphStream { CLIENT }
class Preferences { var isEnabled = true }
class Animator { var frames = 0; var ended = 0; fun setFrame(stream: GlyphStream, frame: FloatArray) { frames++ }; fun endFrames(stream: GlyphStream) { ended++ } }
class Glyph { val preferences = Preferences(); val animator = Animator() }
