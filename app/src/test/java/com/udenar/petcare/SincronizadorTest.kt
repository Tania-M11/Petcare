package com.udenar.petcare

import com.udenar.petcare.data.sync.AlmacenCursores
import com.udenar.petcare.data.sync.FuenteRemota
import com.udenar.petcare.data.sync.Pendiente
import com.udenar.petcare.data.sync.Sincronizador
import com.udenar.petcare.data.sync.TablaSincronizable
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** Motor de sincronización con un servidor y una tabla en memoria. */
class SincronizadorTest {

    /** Servidor falso: pone actualizado_en con su propio reloj, como el trigger real. */
    private class ServidorFalso : FuenteRemota {
        val filas = linkedMapOf<String, JsonObject>()
        private var tic = 0L
        var consultas = mutableListOf<String?>()

        private fun ahora(): String = OffsetDateTime.of(2026, 11, 19, 12, 0, 0, 0, ZoneOffset.UTC)
            .plusSeconds(tic++).toString()

        fun escribir(id: String, nombre: String, eliminado: Boolean = false) {
            filas[id] = fila(id, nombre, eliminado, ahora())
        }

        override suspend fun subir(tabla: String, filas: List<JsonObject>) = filas.map { f ->
            val id = f["id"]!!.jsonPrimitive.content
            val guardada = JsonObject(f + ("actualizado_en" to JsonPrimitive(ahora())))
            this.filas[id] = guardada
            guardada
        }

        override suspend fun cambiosDesde(tabla: String, desde: String?, limite: Int): List<JsonObject> {
            consultas += desde
            return filas.values
                .filter { desde == null || OffsetDateTime.parse(it.txt("actualizado_en")).isAfter(OffsetDateTime.parse(desde)) }
                .sortedBy { OffsetDateTime.parse(it.txt("actualizado_en")) }
                .take(limite)
        }
    }

    /** Tabla local falsa con la misma regla que Room: lo pendiente no se pisa. */
    private class TablaFalsa : TablaSincronizable {
        data class Fila(val nombre: String, val eliminado: Boolean, val pendiente: Boolean, val cambio: Long, val actualizado: String?)

        override val tabla = "mascotas"
        val filas = linkedMapOf<String, Fila>()

        fun editar(id: String, nombre: String, cambio: Long, eliminado: Boolean = false) {
            filas[id] = Fila(nombre, eliminado, true, cambio, filas[id]?.actualizado)
        }

        override suspend fun pendientes() = filas.filter { it.value.pendiente }.map { (id, f) ->
            Pendiente(id, f.cambio, fila(id, f.nombre, f.eliminado, null))
        }

        override suspend fun confirmar(id: String, cambioLocal: Long, actualizadoEn: String) {
            val f = filas[id] ?: return
            if (f.cambio == cambioLocal) filas[id] = f.copy(pendiente = false, actualizado = actualizadoEn)
            filas.entries.removeIf { it.value.eliminado && !it.value.pendiente }
        }

        override suspend fun aplicarRemotas(filas: List<JsonObject>) {
            for (r in filas) {
                val id = r.txt("id")
                if (this.filas[id]?.pendiente == true) continue
                if (r.txt("eliminado") == "true") this.filas.remove(id)
                else this.filas[id] = Fila(r.txt("nombre"), false, false, 0, r.txt("actualizado_en"))
            }
        }
    }

    private class CursoresFalsos : AlmacenCursores {
        val mapa = mutableMapOf<String, String?>()
        override suspend fun cursor(tabla: String) = mapa[tabla]
        override suspend fun guardar(tabla: String, cursor: String?, sincronizadoEn: Long) {
            mapa[tabla] = cursor
        }
    }

    private val reloj = Clock.fixed(Instant.parse("2026-11-19T12:00:00Z"), ZoneOffset.UTC)
    private val servidor = ServidorFalso()
    private val tabla = TablaFalsa()
    private val cursores = CursoresFalsos()
    private val motor = Sincronizador(setOf(tabla), servidor, cursores, reloj)

    @Test
    fun subeLoPendienteYLoConfirma() = runTest {
        tabla.editar("a", "Luna", cambio = 1)
        val r = motor.sincronizar()
        assertEquals(1, r.subidas)
        assertEquals("Luna", servidor.filas["a"]!!.txt("nombre"))
        assertTrue(tabla.filas["a"]!!.actualizado != null)
        assertTrue(!tabla.filas["a"]!!.pendiente)
    }

    @Test
    fun bajaLoNuevoYAvanzaElCursor() = runTest {
        servidor.escribir("b", "Max")
        servidor.escribir("c", "Kira")
        motor.sincronizar()
        assertEquals(setOf("b", "c"), tabla.filas.keys)
        assertEquals(servidor.filas["c"]!!.txt("actualizado_en"), cursores.mapa["mascotas"])

        // La segunda vez pregunta desde el cursor menos el margen
        motor.sincronizar()
        assertEquals(Sincronizador.restarMargen(cursores.mapa["mascotas"]!!), servidor.consultas.last())
    }

    @Test
    fun unCambioLocalPendienteNoSePisa() = runTest {
        servidor.escribir("a", "Luna")
        motor.sincronizar()
        servidor.escribir("a", "Luna (servidor)")
        tabla.editar("a", "Luna (teléfono)", cambio = 5)
        motor.sincronizar()
        // El teléfono sube su versión y queda como la última escritura
        assertEquals("Luna (teléfono)", servidor.filas["a"]!!.txt("nombre"))
        assertEquals("Luna (teléfono)", tabla.filas["a"]!!.nombre)
    }

    @Test
    fun siSeEditaDuranteLaSubidaQuedaPendiente() = runTest {
        tabla.editar("a", "Luna", cambio = 1)
        val pend = tabla.pendientes()
        tabla.editar("a", "Luna 2", cambio = 2) // edición mientras viaja la primera
        tabla.confirmar("a", pend.first().cambioLocal, "2026-11-19T12:00:00Z")
        assertTrue(tabla.filas["a"]!!.pendiente)
    }

    @Test
    fun eliminarSeSincronizaComoMarca() = runTest {
        servidor.escribir("a", "Luna")
        motor.sincronizar()
        servidor.escribir("a", "Luna", eliminado = true)
        motor.sincronizar()
        assertNull(tabla.filas["a"])

        tabla.editar("z", "Temporal", cambio = 1)
        tabla.editar("z", "Temporal", cambio = 2, eliminado = true)
        motor.sincronizar()
        assertNull(tabla.filas["z"])
        assertEquals("true", servidor.filas["z"]!!.txt("eliminado"))
    }

    @Test
    fun descargaPorPaginas() = runTest {
        repeat(Sincronizador.LIMITE_BAJADA + 20) { servidor.escribir("m$it", "M$it") }
        val r = motor.sincronizar()
        assertEquals(Sincronizador.LIMITE_BAJADA + 20, tabla.filas.size)
        assertEquals(Sincronizador.LIMITE_BAJADA + 20, r.bajadas)
    }

    companion object {
        fun fila(id: String, nombre: String, eliminado: Boolean, actualizado: String?) = buildJsonObject {
            put("id", id)
            put("nombre", nombre)
            put("eliminado", eliminado)
            if (actualizado != null) put("actualizado_en", actualizado)
        }

        fun JsonObject.txt(k: String) = this[k]!!.jsonPrimitive.content
    }
}
