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

    val avisos = remember { SnackbarHostState() }
    val alcance = rememberCoroutineScope()
    val avisar: (String) -> Unit = { msj ->
        alcance.launch {
            avisos.currentSnackbarData?.dismiss()
            avisos.showSnackbar(msj)
        }
    }

    Scaffold(
        containerColor = Pc.Crema,
        snackbarHost = { SnackbarHost(avisos) },
        // La barra solo se ve en las 4 pestañas (no en pantallas completas como Intenciones)
        bottomBar = { if (actual != null) BarraInferior(PestanaPersonal.entries, actual = actual, onElegir = { nav.irAPestana(it) }) },
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
                    avisar = avisar,
                )
            }
            composable<RutaMascotas> {
                MascotasScreen(
                    avisar = avisar,
                    onAgregar = { nav.navigate(RutaFormMascota()) },
                    onCuidar = { nav.navigate(RutaCuidarCodigo()) },
                    onQrLeido = { nav.navigate(RutaResultadoQr(it)) },
                    onAbrir = { nav.navigate(RutaDetalleMascota(it)) },
                )
            }
            composable<RutaFormMascota> {
                FormMascotaScreen(
                    onVolver = { nav.popBackStack() },
                    onGuardada = { id ->
                        nav.popBackStack()
                        // Si era nueva (se abrió desde la lista), se muestra su ficha
                        if (nav.currentDestination?.hasRoute(RutaDetalleMascota::class) != true) nav.navigate(RutaDetalleMascota(id))
                    },
                )
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
                    onVisita = { idVisita ->
                        val mascotaId = nav.currentBackStackEntry?.toRoute<RutaDetalleMascota>()?.id ?: return@DetalleMascotaScreen
                        nav.navigate(RutaFormVisita(mascotaId, idVisita))
                    },
                    avisar = avisar,
                )
            }
            composable<RutaFormCuidado> { FormCuidadoScreen(onListo = { nav.popBackStack() }) }
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
            composable<RutaFormVisita> { com.udenar.petcare.ui.mascotas.FormVisitaScreen(onListo = { nav.popBackStack() }) }
            composable<RutaServicios> { ServiciosScreen(avisar = avisar, onVeterinarios = { nav.navigate(RutaDirectorioVet) }) }
            composable<RutaPerfil> {
                PerfilScreen(avisar = avisar, irAIntenciones = { nav.navigate(RutaIntenciones) }, onPerfilVeterinario = { nav.navigate(RutaFormVeterinario) })
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
            composable<RutaDetalleVet> { com.udenar.petcare.ui.servicios.DetalleVeterinarioScreen(onVolver = { nav.popBackStack() }, avisar = avisar) }
            composable<RutaIntenciones> {
                IntencionesScreen(onListo = { nav.popBackStack() }, onVolver = { nav.popBackStack() })
            }
        }
    }
}

private fun NavHostController.irAPestana(pestana: PestanaPersonal) = navigate(pestana.ruta) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
