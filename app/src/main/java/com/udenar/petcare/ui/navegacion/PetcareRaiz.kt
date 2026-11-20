package com.udenar.petcare.ui.navegacion

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.udenar.petcare.ui.componentes.PantallaProvisional
import com.udenar.petcare.ui.inicio.InicioScreen

/** Estructura del modo personal: barra inferior con cinco pestañas. */
@Composable
fun PetcareRaiz() {
    val nav = rememberNavController()
    val entradaActual by nav.currentBackStackEntryAsState()
    val destino = entradaActual?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                PestanaPersonal.entries.forEach { pestana ->
                    val seleccionada = destino?.hasRoute(pestana.ruta::class) == true
                    NavigationBarItem(
                        selected = seleccionada,
                        onClick = {
                            nav.navigate(pestana.ruta) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(pestana.icono, contentDescription = null) },
                        label = { Text(pestana.etiqueta) },
                    )
                }
            }
        },
    ) { relleno ->
        NavHost(
            navController = nav,
            startDestination = RutaInicio,
            modifier = Modifier.padding(relleno),
        ) {
            composable<RutaInicio> { InicioScreen() }
            composable<RutaMascotas> {
                PantallaProvisional("Mascotas", "Tus mascotas y las que cuidas (parte 1.3).")
            }
            composable<RutaCalendario> {
                PantallaProvisional("Calendario", "Vista mensual de cuidados (parte 1.12).")
            }
            composable<RutaExplorar> {
                PantallaProvisional("Explorar", "Veterinarios y prestadores (fases 4 y 5).")
            }
            composable<RutaPerfil> {
                PantallaProvisional("Perfil", "Tu cuenta, perfiles profesionales y modos (partes 1.1 y 4.1).")
            }
        }
    }
}
