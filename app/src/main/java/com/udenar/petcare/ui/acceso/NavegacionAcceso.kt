package com.udenar.petcare.ui.acceso

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.Serializable

@Serializable private data object RutaBienvenida
@Serializable private data object RutaRegistro
@Serializable private data object RutaInicioSesion
@Serializable private data object RutaRecuperar

/** Flujo sin sesión: Bienvenida → Registro / Inicio de sesión. */
@Composable
fun NavegacionAcceso() {
    val nav = rememberNavController()
    NavHost(nav, startDestination = RutaBienvenida) {
        composable<RutaBienvenida> {
            BienvenidaScreen(
                onCrearCuenta = { nav.navigate(RutaRegistro) },
                onEntrar = { nav.navigate(RutaInicioSesion) },
            )
        }
        composable<RutaRegistro> {
            RegistroScreen(onVolver = { nav.popBackStack() }, onIrAEntrar = { nav.cambiarA(RutaInicioSesion) })
        }
        composable<RutaInicioSesion> {
            InicioSesionScreen(onVolver = { nav.popBackStack() }, onIrARegistro = { nav.cambiarA(RutaRegistro) }, onOlvide = { nav.navigate(RutaRecuperar) })
        }
        composable<RutaRecuperar> {
            RecuperarClaveScreen(onVolver = { nav.popBackStack() })
        }
    }
}

/** Cambia entre registro e inicio de sesión sin apilar uno sobre otro. */
private fun NavHostController.cambiarA(ruta: Any) = navigate(ruta) {
    popUpTo(RutaBienvenida)
    launchSingleTop = true
}
