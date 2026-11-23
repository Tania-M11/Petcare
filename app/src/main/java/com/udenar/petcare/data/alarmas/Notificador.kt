package com.udenar.petcare.data.alarmas

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.udenar.petcare.MainActivity
import com.udenar.petcare.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Notificaciones locales de recordatorios (canal "Recordatorios", RF-14 y RF-29). */
@Singleton
class Notificador @Inject constructor(@ApplicationContext private val contexto: Context) {

    private val gestor get() = contexto.getSystemService(NotificationManager::class.java)

    fun crearCanales() {
        gestor.createNotificationChannel(
            NotificationChannel(CANAL_RECORDATORIOS, "Recordatorios", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Comidas, medicinas, vacunas, baños y demás cuidados de tus mascotas"
            },
        )
        gestor.createNotificationChannel(
            NotificationChannel(CANAL_SOLICITUDES, "Solicitudes", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Citas con veterinarios, cotizaciones de servicios, reseñas y verificación"
            },
        )
        gestor.createNotificationChannel(
            NotificationChannel(CANAL_CUIDADORES, "Cuidadores", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Quién pide cuidar tus mascotas y las respuestas a tus solicitudes"
            },
        )
    }

    /** Aviso del servidor (RF-29): al tocarlo abre el modo y la pestaña correctos. */
    fun mostrarAvisoRemoto(a: com.udenar.petcare.data.avisos.Aviso) {
        val abrir = Intent(contexto, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .setData("petcare://aviso/${a.id}".toUri())
            .putExtra(EXTRA_MODO, a.modo.name)
            .putExtra(EXTRA_DESTINO, a.destino.name)
        val n = NotificationCompat.Builder(contexto, a.canal)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setColor(0xFFF08A4B.toInt())
            .setContentTitle(a.titulo)
            .setContentText(a.cuerpo)
            .setStyle(NotificationCompat.BigTextStyle().bigText(a.cuerpo))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(contexto, a.id.hashCode(), abrir, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .build()
        publicar("aviso_${a.id}", n)
    }

    /** Toma pendiente con acciones Hecho / Posponer / Omitir. [etiqueta] = id del registro. */
    fun mostrarToma(etiqueta: String, titulo: String, texto: String?, eventoId: String, toma: String, textoHecho: String) {
        val n = base(titulo, texto)
            .addAction(0, textoHecho, accion(ReceptorAccion.HECHO, etiqueta, eventoId, toma))
            .addAction(0, "Posponer 15 min", accion(ReceptorAccion.POSPONER, etiqueta, eventoId, toma))
            .addAction(0, "Omitir", accion(ReceptorAccion.OMITIR, etiqueta, eventoId, toma))
            .build()
        publicar(etiqueta, n)
    }

    /** Aviso anticipado (vacuna o cita en N días): sin acciones, solo informa. */
    fun mostrarAviso(etiqueta: String, titulo: String, texto: String?) = publicar(etiqueta, base(titulo, texto).build())

    fun mostrarPrueba() = publicar(
        "prueba",
        base("¡Los recordatorios funcionan!", "Así te avisará PetCare aunque la app esté cerrada y sin internet.").build(),
    )

    fun retirar(etiqueta: String) = gestor.cancel(etiqueta, ID)

    /** Quita las notificaciones de tomas que ya resolvió alguien (este u otro teléfono). */
    fun retirarResueltas(registrosResueltos: Set<String>) {
        gestor.activeNotifications.filter { it.tag in registrosResueltos }.forEach { gestor.cancel(it.tag, it.id) }
    }

    fun retirarTodas() = gestor.cancelAll()

    private fun base(titulo: String, texto: String?) = NotificationCompat.Builder(contexto, CANAL_RECORDATORIOS)
        .setSmallIcon(R.drawable.ic_notificacion)
        .setColor(0xFFF08A4B.toInt())
        .setContentTitle(titulo)
        .setContentText(texto)
        .setStyle(texto?.let { NotificationCompat.BigTextStyle().bigText(it) })
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setContentIntent(
            PendingIntent.getActivity(
                contexto, 0,
                Intent(contexto, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE,
            ),
        )

    private fun publicar(etiqueta: String, n: android.app.Notification) {
        // Sin permiso (Android 13+) no se puede mostrar; la toma igual aparece en Hoy
        if (!NotificationManagerCompat.from(contexto).areNotificationsEnabled()) return
        gestor.notify(etiqueta, ID, n)
    }

    private fun accion(accion: String, etiqueta: String, eventoId: String, toma: String): PendingIntent =
        PendingIntent.getBroadcast(
            contexto, 0,
            Intent(contexto, ReceptorAccion::class.java)
                .setAction(accion)
                .setData("petcare://toma/$etiqueta".toUri())
                .putExtra(ReceptorAccion.EXTRA_ETIQUETA, etiqueta)
                .putExtra(ReceptorAccion.EXTRA_EVENTO, eventoId)
                .putExtra(ReceptorAccion.EXTRA_TOMA, toma),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        const val CANAL_RECORDATORIOS = "recordatorios"
        const val CANAL_SOLICITUDES = "solicitudes"
        const val CANAL_CUIDADORES = "cuidadores"
        const val EXTRA_MODO = "aviso_modo"
        const val EXTRA_DESTINO = "aviso_destino"
        private const val ID = 1
    }
}
