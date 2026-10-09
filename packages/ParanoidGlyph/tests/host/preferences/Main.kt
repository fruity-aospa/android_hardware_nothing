import android.content.Context
import android.content.SharedPreferences
import co.aospa.glyph.data.source.GlyphPreferences
import co.aospa.glyph.data.source.GlyphConfig
import co.aospa.glyph.data.model.ScheduleMode
import java.time.DayOfWeek
fun main() {
val p=SharedPreferences(mutableMapOf("glyph_enable" to false,"glyph_schedule_enabled" to true,"glyph_schedule_start_hour" to 23,"glyph_schedule_start_minute" to 45,"glyph_schedule_end_hour" to 6,"glyph_schedule_end_minute" to 20,"glyph_schedule_days" to setOf("1","2","7")))
val c=Context(p); c.contentResolver.secure.putAll(mapOf("glyph_enable" to "1","glyph_settings_flip_toggle" to "0","glyph_settings_call_toggle" to "0","glyph_settings_notifs_toggle" to "1"))
val v=GlyphPreferences(c,GlyphConfig()); check(v.isEnabled); check(!v.isFlipEnabled); check(!v.isCallEnabled); check(v.areNotificationsEnabled); check(v.scheduleMode==ScheduleMode.CUSTOM); check(v.scheduleStart==1425); check(v.scheduleEnd==380); check(v.scheduleDays==setOf(DayOfWeek.SUNDAY,DayOfWeek.MONDAY,DayOfWeek.SATURDAY))
v.isCallEnabled=true; v.isFlipEnabled=true; p.values["glyph_schedule_days"]=setOf("3"); val v2=GlyphPreferences(c,GlyphConfig()); check(v2.isCallEnabled); check(v2.isFlipEnabled); check(v2.scheduleDays==setOf(DayOfWeek.WEDNESDAY))
val fresh=GlyphPreferences(Context(SharedPreferences()),GlyphConfig()); check(fresh.scheduleMode==ScheduleMode.NONE); check(fresh.scheduleDays.size==7)
val all=SharedPreferences(mutableMapOf("glyph_schedule_enabled" to false,"glyph_schedule_days" to (1..7).map(Int::toString).toSet())); val migrated=GlyphPreferences(Context(all),GlyphConfig()); check(migrated.scheduleDays.size==7); check(migrated.scheduleMode==ScheduleMode.NONE)
println("PASS: legacy Secure toggles, exactquiet time/weekdays, all7 day mapping, once-only migration and freshdefaults")
}
