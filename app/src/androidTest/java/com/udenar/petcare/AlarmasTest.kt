package com.udenar.petcare

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.udenar.petcare.data.alarmas.ReceptorAccion
import com.udenar.petcare.data.alarmas.ReceptorAlarma
import com.udenar.petcare.data.alarmas.RelojAlarmasAndroid
import com.udenar.petcare.domain.cuidados.EstadoRegistro
import com.udenar.petcare.domain.cuidados.EventoCuidado
import com.udenar.petcare.domain.cuidados.Frecuencia
import com.udenar.petcare.domain.cuidados.MotorCuidados
import com.udenar.petcare.domain.cuidados.RepositorioCuidados
import com.udenar.petcare.domain.cuidados.TipoCuidado
import com.udenar.petcare.domain.cuidados.TipoFrecuencia
import com.udenar.petcare.domain.mascotas.RepositorioMascotas
import com.udenar.petcare.domain.modelo.Especie
import com.udenar.petcare.domain.modelo.Mascota
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** Parte 2.4: alarmas y notificaciones con botones. */
@HiltAndroidTest
class AlarmasTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val permiso: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @Inject lateinit var mascotas: RepositorioMascotas
    @Inject lateinit var cuidados: RepositorioCuidados
    @Inject lateinit var reloj: RelojAlarmasFalso
    @Inject lateinit var preferencias: com.udenar.petcare.data.local.PreferenciaDao
    @Inject lateinit var planificador: com.udenar.petcare.domain.cuidados.ProgramadorRecordatorios

    private val contexto: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val notificaciones get() = contexto.getSystemService(NotificationManager::class.java)

    @Before
    fun preparar() = hilt.inject()

    /** Un medicamento a una hora futura de hoy (o mañana si ya es muy tarde). */
    private suspend fun crearMedicamento(): Pair<EventoCuidado, LocalDateTime> {
        val ahora = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
        val hora = ahora.plusHours(1).toLocalTime()
        val inicio = if (hora.isBefore(ahora.toLocalTime())) LocalDate.now().plusDays(1) else LocalDate.now()
        mascotas.guardar(Mascota("m1", "Luna", Especie.GATO))
        val evento = EventoCuidado(
            "e1", "m1", TipoCuidado.MEDICAMENTO, "Gotas", Frecuencia(TipoFrecuencia.DIARIO, horas = listOf(hora)),
            fechaInicio = inicio, detalle = mapOf("dosis" to "2 gotas"),
        )
        cuidados.guardarEvento(evento)
        return evento to inicio.atTime(hora)
    }

    @Test
    fun alGuardarSeProgramaYAlMarcarPasaALaSiguiente() = runBlocking {
        val (evento, toma) = crearMedicamento()
        val zona = ZoneId.systemDefault()
        assertEquals(toma.atZone(zona).toInstant(), reloj.programadas["e1"])

        // Marcada antes de que suene: la alarma pasa al día siguiente
        val t = cuidados.agenda(toma.toLocalDate()).first().single()
        cuidados.registrar(t, EstadoRegistro.ADMINISTRADA)
        assertEquals(toma.plusDays(1).atZone(zona).toInstant(), reloj.programadas["e1"])

        // Al eliminar el cuidado se cancela
        cuidados.eliminarEvento(evento.id)
        assertFalse("e1" in reloj.programadas)
    }

    @Test
    fun laNotificacionAparecePorLaAlarmaYHechoLaRegistra() = runBlocking {
        val (evento, toma) = crearMedicamento()
        val etiqueta = MotorCuidados.idRegistro(evento.id, toma)

        // Simula que AlarmManager dispara la alarma
        contexto.sendBroadcast(
            Intent(contexto, ReceptorAlarma::class.java)
                .setAction(RelojAlarmasAndroid.ACCION_ALARMA)
                .setData("petcare://alarma/e1".toUri())
                .putExtra(RelojAlarmasAndroid.EXTRA_CLAVE, "e1")
                .putExtra(RelojAlarmasAndroid.EXTRA_EVENTO, evento.id)
                .putExtra(RelojAlarmasAndroid.EXTRA_TOMA, toma.toString()),
        )
        val visible = esperar { notificaciones.activeNotifications.any { it.tag == etiqueta } }
        assertTrue("La notificación no apareció", visible)
        val n = notificaciones.activeNotifications.single { it.tag == etiqueta }.notification
        assertEquals("Luna: Gotas", n.extras.getString("android.title"))
        assertEquals(listOf("Hecho", "Posponer 15 min", "Omitir"), n.actions.map { it.title.toString() })

        // Toca "Hecho" en la notificación
        contexto.sendBroadcast(
            Intent(contexto, ReceptorAccion::class.java)
                .setAction(ReceptorAccion.HECHO)
                .setData("petcare://toma/$etiqueta".toUri())
                .putExtra(ReceptorAccion.EXTRA_ETIQUETA, etiqueta)
                .putExtra(ReceptorAccion.EXTRA_EVENTO, evento.id)
                .putExtra(ReceptorAccion.EXTRA_TOMA, toma.toString()),
        )
        assertTrue(esperar { cuidados.agenda(toma.toLocalDate()).first().singleOrNull()?.registro?.estado == EstadoRegistro.ADMINISTRADA })
        assertTrue(esperar { notificaciones.activeNotifications.none { it.tag == etiqueta } })
    }

    @Test
    fun silenciarLaMascotaEnEsteTelefonoCancelaSusAlarmas(): Unit = runBlocking {
        crearMedicamento()
        assertTrue("e1" in reloj.programadas)
        preferencias.guardar(com.udenar.petcare.data.local.PreferenciaMascota("m1", false))
        planificador.reprogramar()
        assertFalse("e1" in reloj.programadas)
        preferencias.guardar(com.udenar.petcare.data.local.PreferenciaMascota("m1", true))
        planificador.reprogramar()
        assertTrue("e1" in reloj.programadas)
    }

    @Test
    fun siOtraPersonaYaLaResolvioNoSeMuestra() = runBlocking {
        val (evento, toma) = crearMedicamento()
        val t = cuidados.agenda(toma.toLocalDate()).first().single()
        cuidados.registrar(t, EstadoRegistro.ADMINISTRADA)
        contexto.sendBroadcast(
            Intent(contexto, ReceptorAlarma::class.java)
                .setAction(RelojAlarmasAndroid.ACCION_ALARMA)
                .setData("petcare://alarma/e1".toUri())
                .putExtra(RelojAlarmasAndroid.EXTRA_CLAVE, "e1")
                .putExtra(RelojAlarmasAndroid.EXTRA_EVENTO, evento.id)
                .putExtra(RelojAlarmasAndroid.EXTRA_TOMA, toma.toString()),
        )
        delay(1_500)
        assertTrue(notificaciones.activeNotifications.none { it.tag == MotorCuidados.idRegistro(evento.id, toma) })
    }

    private suspend fun esperar(condicion: suspend () -> Boolean): Boolean {
        repeat(50) {
            if (condicion()) return true
            delay(100)
        }
        return false
    }

}
