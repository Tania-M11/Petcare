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
import com.udenar.petcare.ui.guia.IntencionesScreen
import com.udenar.petcare.ui.hoy.HoyScreen
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
        bottomBar = { if (actual != null) BarraInferior(actual = actual, onElegir = { nav.irAPestana(it) }) },
    ) { relleno ->
        NavHost(
            navController = nav,
            startDestination = RutaHoy,
            modifier = Modifier
                .padding(relleno)
                .consumeWindowInsets(relleno),
        ) {
            composable<RutaHoy> { HoyScreen(irAMascotas = { nav.irAPestana(PestanaPersonal.MASCOTAS) }, avisar = avisar) }
            composable<RutaMascotas> { MascotasScreen(avisar = avisar) }
            composable<RutaServicios> { ServiciosScreen(avisar = avisar) }
            composable<RutaPerfil> { PerfilScreen(avisar = avisar, irAIntenciones = { nav.navigate(RutaIntenciones) }) }
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
