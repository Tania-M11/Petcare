package com.udenar.petcare.data.alarmas

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.net.toUri
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject

/** Alarmas programadas en AlarmManager (para poder cancelar las que ya no aplican). Spec 9.5. */
@Entity(tableName = "alarmas")
data class AlarmaEntidad(
    @PrimaryKey val clave: String,
    val eventoId: String,
    /** Toma a la que corresponde, "2026-11-19T08:00". */
    val toma: String,
    val disparaEn: Long,
    val esAviso: Boolean,
)

@Dao
interface AlarmaDao {
    @Query("SELECT * FROM alarmas")
    suspend fun todas(): List<AlarmaEntidad>

    @Upsert
    suspend fun guardar(a: AlarmaEntidad)

    @Query("DELETE FROM alarmas WHERE clave = :clave")
    suspend fun borrar(clave: String)
}

/** Lo que viaja dentro de la alarma hasta el receptor. */
data class DatosAlarma(val clave: String, val eventoId: String?, val toma: String?, val esAviso: Boolean, val prueba: Boolean = false)

/** Envoltorio de AlarmManager (las pruebas lo reemplazan). */
interface RelojAlarmas {
    fun programar(datos: DatosAlarma, cuando: Instant)
    fun cancelar(clave: String)
    /** Android 12+: ¿el usuario permite alarmas a la hora exacta? */
    fun puedeExactas(): Boolean
}

class RelojAlarmasAndroid @Inject constructor(@ApplicationContext private val contexto: Context) : RelojAlarmas {
    private val am get() = contexto.getSystemService(AlarmManager::class.java)

    override fun puedeExactas(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()

    override fun programar(datos: DatosAlarma, cuando: Instant) {
        val pi = pendiente(datos.clave, datos)
        if (puedeExactas()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cuando.toEpochMilli(), pi)
        } else {
            // Sin permiso de hora exacta: Android puede retrasarla unos minutos
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cuando.toEpochMilli(), pi)
        }
    }

    override fun cancelar(clave: String) = am.cancel(pendiente(clave, null))

    private fun pendiente(clave: String, datos: DatosAlarma?): PendingIntent {
        val intent = Intent(contexto, ReceptorAlarma::class.java)
            .setAction(ACCION_ALARMA)
            // La URI distingue una alarma de otra (filterEquals)
            .setData("petcare://alarma/$clave".toUri())
        if (datos != null) {
            intent.putExtra(EXTRA_CLAVE, datos.clave)
                .putExtra(EXTRA_EVENTO, datos.eventoId)
                .putExtra(EXTRA_TOMA, datos.toma)
                .putExtra(EXTRA_AVISO, datos.esAviso)
                .putExtra(EXTRA_PRUEBA, datos.prueba)
        }
        return PendingIntent.getBroadcast(contexto, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        const val ACCION_ALARMA = "com.udenar.petcare.ALARMA"
        const val EXTRA_CLAVE = "clave"
        const val EXTRA_EVENTO = "evento"
        const val EXTRA_TOMA = "toma"
        const val EXTRA_AVISO = "aviso"
        const val EXTRA_PRUEBA = "prueba"

        fun datos(intent: Intent) = DatosAlarma(
            clave = intent.getStringExtra(EXTRA_CLAVE).orEmpty(),
            eventoId = intent.getStringExtra(EXTRA_EVENTO),
            toma = intent.getStringExtra(EXTRA_TOMA),
            esAviso = intent.getBooleanExtra(EXTRA_AVISO, false),
            prueba = intent.getBooleanExtra(EXTRA_PRUEBA, false),
        )
    }
}
