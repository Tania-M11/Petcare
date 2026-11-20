package com.udenar.petcare.ui.raiz

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udenar.petcare.R
import com.udenar.petcare.domain.modelo.EstadoSesion
import com.udenar.petcare.ui.acceso.NavegacionAcceso
import com.udenar.petcare.ui.navegacion.PetcareRaiz
import com.udenar.petcare.ui.theme.Pc

@Composable
fun PetcareApp(viewModel: RaizViewModel = hiltViewModel()) {
    val sesion by viewModel.sesion.collectAsStateWithLifecycle()
    Crossfade(sesion, label = "sesion") { estado ->
        when (estado) {
            EstadoSesion.CARGANDO -> Box(
                Modifier
                    .fillMaxSize()
                    .background(Pc.Crema),
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(R.drawable.logo_petcare), contentDescription = "PetCare", modifier = Modifier.height(40.dp))
            }
            EstadoSesion.SIN_SESION -> NavegacionAcceso()
            EstadoSesion.CON_SESION -> PetcareRaiz()
        }
    }
}
