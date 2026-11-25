package com.udenar.petcare.ui.acceso

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.udenar.petcare.R
import com.udenar.petcare.ui.componentes.BlobDecorativo
import com.udenar.petcare.ui.componentes.BotonPrincipal
import com.udenar.petcare.ui.componentes.BotonSecundario
import com.udenar.petcare.ui.componentes.grano
import com.udenar.petcare.ui.theme.Pc

/** Primera pantalla sin sesión: Michi saluda y ofrece crear cuenta o entrar. */
@Composable
fun BienvenidaScreen(onCrearCuenta: () -> Unit, onEntrar: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Pc.Crema, Pc.Durazno)))
            .grano(0.35f)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painterResource(R.drawable.logo_petcare),
            contentDescription = "PetCare",
            modifier = Modifier
                .padding(top = 12.dp)
                .height(34.dp),
        )
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            BlobDecorativo(
                colores = listOf(Pc.LavandaClara, Pc.NaranjaClaro),
                semilla = 3,
                modifier = Modifier.size(300.dp),
            )
            Image(
                painterResource(R.drawable.gato_saludo),
                contentDescription = "Michi saludando",
                modifier = Modifier.height(280.dp),
            )
        }
        Text(
            "Cuidar juntos es más fácil",
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center,
        )
        Text(
            "Recordatorios, historial y las personas que cuidan a tu mascota, en un solo lugar.",
            style = MaterialTheme.typography.bodyLarge,
            color = Pc.TintaSuave,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp, bottom = 28.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BotonPrincipal("Crear cuenta", onClick = onCrearCuenta)
            BotonSecundario("Ya tengo cuenta", onClick = onEntrar)
        }
        Spacer(Modifier.height(8.dp))
    }
}
