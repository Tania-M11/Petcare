package com.udenar.petcare.ui.navegacion

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udenar.petcare.ui.cuidados.FormCuidadoScreen
import com.udenar.petcare.ui.guia.IntencionesScreen
import com.udenar.petcare.ui.hoy.HoyScreen
import com.udenar.petcare.ui.mascotas.DetalleMascotaScreen
import com.udenar.petcare.ui.mascotas.FormMascotaScreen
import com.udenar.petcare.ui.mascotas.MascotasScreen
import com.udenar.petcare.ui.perfil.PerfilScreen
import com.udenar.petcare.ui.servicios.ServiciosScreen
import com.udenar.petcare.ui.theme.Pc
import kotlinx.coroutines.launch

/** Estructura del modo personal: 4 pestañas con barra flotante. */
@Composable
fun PetcareRaiz() {
    val nav = rememberNavController()
    val entrada by nav.currentBackStackEntryAsState()
    val actual = PestanaPersonal.entries.firstOrNull { entrada?.destination?.hasRoute(it.ruta::class) == true }

    // Aviso tocado (RF-29): abre la pestaña que corresponde
    val aperturaVm: com.udenar.petcare.ui.raiz.AperturaViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()
    val apertura by aperturaVm.pendiente.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(apertura) {
        aperturaVm.tomar(com.udenar.petcare.domain.modelo.ModoUso.PERSONAL)?.let { a ->
            if (a.destino == com.udenar.petcare.data.avisos.DestinoAviso.PASEO) {
                // Notificación del paseo: vuelve al mapa en vivo (si ya está abierto, no lo repite)
                if (nav.currentDestination?.hasRoute(RutaPaseo::class) != true) nav.navigate(RutaPaseo())
                return@let
            }
            nav.irAPestana(
                when (a.destino) {
                    com.udenar.petcare.data.avisos.DestinoAviso.MASCOTAS -> PestanaPersonal.MASCOTAS
                    com.udenar.petcare.data.avisos.DestinoAviso.SERVICIOS, com.udenar.petcare.data.avisos.DestinoAviso.SOLICITUDES -> PestanaPersonal.SERVICIOS
                    com.udenar.petcare.data.avisos.DestinoAviso.PERFIL -> PestanaPersonal.PERFIL
                    com.udenar.petcare.data.avisos.DestinoAviso.HOY, com.udenar.petcare.data.avisos.DestinoAviso.PASEO -> PestanaPersonal.HOY
                },
            )
        }
    }
    val avisos = remember { SnackbarHostState() }
    val alcance = rememberCoroutineScope()
    val avisar: (String) -> Unit = { msj ->
        alcance.launch {
            avisos.currentSnackbarData?.dismiss()
            avisos.showSnackbar(msj)
        }
    }
    // Eligió "Soy veterinario/a" u "Ofrezco servicios": se abren sus formularios en ese orden
    val formularios by aperturaVm.formulariosPendientes.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(formularios) {
        val cola = aperturaVm.tomarFormularios()
        if (cola.isNotEmpty()) {
            nav.popBackStack(RutaIntenciones, inclusive = true)
            cola.reversed().forEach {
                nav.navigate(if (it == com.udenar.petcare.domain.guia.Intencion.VETERINARIO) RutaFormVeterinario else RutaFormPrestador())
            }
            avisar("Completa tu perfil profesional. Si prefieres, termínalo luego desde Perfil")
        }
    }

    Scaffold(
        containerColor = Pc.Crema,
        snackbarHost = { SnackbarHost(avisos) { datos -> com.udenar.petcare.ui.componentes.AvisoMichi(datos.visuals.message) } },
        // La barra solo se ve en las 4 pestañas (no en pantallas completas como Intenciones)
        bottomBar = {
            if (actual != null) {
                androidx.compose.foundation.layout.Column {
                    // Paseo en curso: visible desde cualquier pestaña (en Hoy ya está su tarjeta grande)
                    if (actual != PestanaPersonal.HOY) {
                        com.udenar.petcare.ui.paseos.BarraPaseoEnCurso(onAbrir = { nav.navigate(RutaPaseo()) }, modifier = Modifier.padding(top = 8.dp))
                    }
                    BarraInferior(PestanaPersonal.entries, actual = actual, onElegir = { nav.irAPestana(it) })
                }
            }
        },
    ) { relleno ->
        NavHost(
            navController = nav,
            startDestination = RutaHoy,
            modifier = Modifier
                .padding(relleno)
                .consumeWindowInsets(relleno),
        ) {
            composable<RutaHoy> {
                HoyScreen(
                    irAMascotas = { nav.irAPestana(PestanaPersonal.MASCOTAS) },
                    onAbrirMascota = { nav.navigate(RutaDetalleMascota(it)) },
                    onNuevoCuidado = { mascotaId, clase -> nav.navigate(RutaFormCuidado(mascotaId, clase)) },
                    onPasear = { mascotaId -> nav.navigate(RutaPaseo(mascotaId)) },
                    onPerfilVeterinario = { nav.navigate(RutaFormVeterinario) },
                    onPerfilPrestador = { nav.navigate(RutaFormPrestador()) },
                    onVeterinarios = { nav.navigate(RutaDirectorioVet) },
                    onEditarMascota = { nav.navigate(RutaFormMascota(it)) },
                    onCarne = { nav.navigate(RutaCarne(it)) },
                    onPrestadores = { nav.navigate(RutaDirectorioPres) },
                    avisar = avisar,
                )
            }
            composable<RutaCalendario> { com.udenar.petcare.ui.calendario.CalendarioScreen() }
            composable<RutaHistorial> { com.udenar.petcare.ui.historial.HistorialScreen() }
            composable<RutaMascotas> {
                MascotasScreen(
                    avisar = avisar,
                    onAgregar = { nav.navigate(RutaFormMascota()) },
                    onCuidar = { nav.navigate(RutaCuidarCodigo()) },
                    onQrLeido = { nav.navigate(RutaResultadoQr(it)) },
                    onAbrir = { nav.navigate(RutaDetalleMascota(it)) },
                )
            }
            composable<RutaFormMascota> { entrada ->
                val onGuardada: (String) -> Unit = { id ->
                    nav.popBackStack()
                    // Si era nueva (se abrió desde la lista), se muestra su ficha
                    if (nav.currentDestination?.hasRoute(RutaDetalleMascota::class) != true) {
                        avisar("¡Bienvenida a la familia! Ahora crea su primer recordatorio")
                        nav.navigate(RutaDetalleMascota(id))
                    } else {
                        avisar("¡Listo! La ficha quedó guardada")
                    }
                }
                // Nueva: recorrido guiado; editar: la ficha completa
                if (entrada.toRoute<RutaFormMascota>().id == null) {
                    com.udenar.petcare.ui.mascotas.NuevaMascotaScreen(onVolver = { nav.popBackStack() }, onGuardada = onGuardada)
                } else {
                    FormMascotaScreen(onVolver = { nav.popBackStack() }, onGuardada = onGuardada)
                }
            }
            composable<RutaDetalleMascota> {
                DetalleMascotaScreen(
                    onVolver = { nav.popBackStack() },
                    onEditar = { nav.navigate(RutaFormMascota(it)) },
                    onCuidado = { clase, idCuidado ->
                        val mascotaId = nav.currentBackStackEntry?.toRoute<RutaDetalleMascota>()?.id ?: return@DetalleMascotaScreen
                        nav.navigate(RutaFormCuidado(mascotaId, clase, idCuidado))
                    },
                    onCarne = { nav.navigate(RutaCarne(it)) },
                    onPasear = { nav.navigate(RutaPaseo(it)) },
                    onPaseo = { nav.navigate(RutaDetallePaseo(it)) },
                    onVeterinarios = { nav.navigate(RutaDirectorioVet) },
                    onSintoma = { nav.navigate(RutaNuevoSintoma(it)) },
                    onVisita = { idVisita ->
                        val mascotaId = nav.currentBackStackEntry?.toRoute<RutaDetalleMascota>()?.id ?: return@DetalleMascotaScreen
                        nav.navigate(RutaFormVisita(mascotaId, idVisita))
                    },
                    avisar = avisar,
                )
            }
            composable<RutaNuevoSintoma> {
                com.udenar.petcare.ui.mascotas.NuevoSintomaScreen(
                    onCerrar = { nav.popBackStack() },
                    onGuardado = {
                        nav.popBackStack()
                        avisar("Anotado. Si te preocupa, muéstraselo a su veterinario")
                    },
                )
            }
            composable<RutaFormCuidado> {
                val nuevo = it.toRoute<RutaFormCuidado>().id == null
                val listo = {
                    avisar(if (nuevo) "¡Recordatorio listo! Te aviso a tiempo" else "¡Listo! Cuidados actualizados")
                    nav.popBackStack()
                    Unit
                }
                // Nuevo: recorrido guiado por pasos; editar: el formulario completo
                if (nuevo) com.udenar.petcare.ui.cuidados.NuevoCuidadoScreen(onListo = { nav.popBackStack() }, onGuardado = listo)
                else FormCuidadoScreen(onListo = listo)
            }
            composable<RutaCuidarCodigo> { com.udenar.petcare.ui.compartir.CuidarConCodigoScreen(onVolver = { nav.popBackStack() }) }
            composable<RutaPaseo> { com.udenar.petcare.ui.paseos.PaseoScreen(onVolver = { nav.popBackStack() }, avisar = avisar) }
            composable<RutaDetallePaseo> {
                val vm: com.udenar.petcare.ui.paseos.DetallePaseoViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()
                val paseo by vm.paseo.collectAsStateWithLifecycle()
                com.udenar.petcare.ui.paseos.DetallePaseoScreen(paseo, onVolver = { nav.popBackStack() }, onEliminar = { vm.eliminar { nav.popBackStack() } })
            }
            composable<RutaCarne> { com.udenar.petcare.ui.carne.CarneScreen(onVolver = { nav.popBackStack() }, avisar = avisar) }
            composable<RutaResultadoQr> { entrada ->
                com.udenar.petcare.ui.compartir.ResultadoQrScreen(
                    texto = entrada.toRoute<RutaResultadoQr>().texto,
                    onVolver = { nav.popBackStack() },
                    onQuieroCuidar = { codigo -> nav.navigate(RutaCuidarCodigo(codigo)) { popUpTo<RutaResultadoQr> { inclusive = true } } },
                )
            }
            composable<RutaFormVisita> {
                if (it.toRoute<RutaFormVisita>().id == null) {
                    com.udenar.petcare.ui.mascotas.NuevaVisitaScreen(
                        onCerrar = { nav.popBackStack() },
                        onGuardada = {
                            avisar("¡Visita guardada en su historial!")
                            nav.popBackStack()
                        },
                    )
                    return@composable
                }
                com.udenar.petcare.ui.mascotas.FormVisitaScreen(onListo = {
                    avisar("¡Visita guardada en su historial!")
                    nav.popBackStack()
                })
            }
            composable<RutaServicios> {
                ServiciosScreen(avisar = avisar, onVeterinarios = { nav.navigate(RutaDirectorioVet) }, onPrestadores = { nav.navigate(RutaDirectorioPres) })
            }
            composable<RutaDirectorioPres> {
                com.udenar.petcare.ui.servicios.DirectorioPrestadoresScreen(onVolver = { nav.popBackStack() }, onAbrir = { nav.navigate(RutaDetallePrestador(it)) }, avisar = avisar)
            }
            composable<RutaDetallePrestador> { entrada ->
                com.udenar.petcare.ui.servicios.DetallePrestadorScreen(
                    onVolver = { nav.popBackStack() },
                    onSolicitar = { nav.navigate(RutaSolicitarPrestador(entrada.toRoute<RutaDetallePrestador>().id)) },
                    avisar = avisar,
                )
            }
            composable<RutaSolicitarPrestador> {
                com.udenar.petcare.ui.servicios.SolicitarPrestadorScreen(
                    onVolver = { nav.popBackStack() },
                    onEnviada = {
                        avisar("Solicitud enviada. Te avisaremos cuando responda.")
                        nav.popBackStack(RutaServicios, inclusive = false)
                    },
                    avisar = avisar,
                )
            }
            composable<RutaFormPrestador> {
                com.udenar.petcare.ui.profesional.FormPrestadorScreen(
                    onVolver = { nav.popBackStack() },
                    // Al activarlo, la raíz cambia sola al modo prestador
                    onGuardado = { activado -> if (!activado) nav.popBackStack() },
                    avisar = avisar,
                )
            }
            composable<RutaPerfil> {
                PerfilScreen(avisar = avisar, irAIntenciones = { nav.navigate(RutaIntenciones) }, onPerfilVeterinario = { nav.navigate(RutaFormVeterinario) }, onPerfilPrestador = { nav.navigate(RutaFormPrestador(it)) })
            }
            composable<RutaFormVeterinario> {
                com.udenar.petcare.ui.profesional.FormVeterinarioScreen(
                    onVolver = { nav.popBackStack() },
                    // Al activarlo, la raíz cambia sola al modo veterinario
                    onGuardado = { activado -> if (!activado) nav.popBackStack() },
                    avisar = avisar,
                )
            }
            composable<RutaDirectorioVet> {
                com.udenar.petcare.ui.servicios.DirectorioVeterinariosScreen(onVolver = { nav.popBackStack() }, onAbrir = { nav.navigate(RutaDetalleVet(it)) }, avisar = avisar)
            }
            composable<RutaDetalleVet> { entrada ->
                com.udenar.petcare.ui.servicios.DetalleVeterinarioScreen(
                    onVolver = { nav.popBackStack() },
                    onSolicitar = { nav.navigate(RutaSolicitarVet(entrada.toRoute<RutaDetalleVet>().id)) },
                    avisar = avisar,
                )
            }
            composable<RutaSolicitarVet> {
                com.udenar.petcare.ui.servicios.SolicitarVetScreen(
                    onVolver = { nav.popBackStack() },
                    onEnviada = {
                        avisar("Solicitud enviada. Te avisaremos cuando responda.")
                        nav.popBackStack(RutaServicios, inclusive = false)
                    },
                    avisar = avisar,
                )
            }
            composable<RutaIntenciones> {
                IntencionesScreen(
                    // Si se abrieron formularios profesionales, Intenciones ya salió de la pila
                    onListo = { if (nav.currentDestination?.hasRoute(RutaIntenciones::class) == true) nav.popBackStack() },
                    onVolver = { nav.popBackStack() },
                )
            }
        }
    }
}

private fun NavHostController.irAPestana(pestana: PestanaPersonal) = navigate(pestana.ruta) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
