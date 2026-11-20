package com.udenar.petcare.ui.inicio

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.udenar.petcare.ui.componentes.PantallaProvisional

@Composable
fun InicioScreen(
    modifier: Modifier = Modifier,
    viewModel: InicioViewModel = hiltViewModel(),
) {
    PantallaProvisional(
        titulo = viewModel.fechaDeHoy,
        descripcion = "Aquí verás los cuidados de hoy de tus mascotas.",
        modifier = modifier,
    )
}
