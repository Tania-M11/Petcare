package com.udenar.petcare.ui.hoy

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udenar.petcare.R
import com.udenar.petcare.ui.componentes.BotonCircular
import com.udenar.petcare.ui.componentes.EstadoVacio
import com.udenar.petcare.ui.componentes.TarjetaIlustrada
import com.udenar.petcare.ui.componentes.TituloSeccion
import com.udenar.petcare.ui.theme.Pc
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FORMATO_DIA = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es-CO"))

/** Pestaña Hoy: saludo, tira de la semana, primeros pasos y pendientes del día elegido. */
@Composable
fun HoyScreen(
    irAMascotas: () -> Unit,
    avisar: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HoyViewModel = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Image(
                    painterResource(R.drawable.logo_petcare),
                    contentDescription = "PetCare",
                    modifier = Modifier.height(26.dp),
                )
                Text(
                    "Hola,",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Pc.TintaSuave,
                    modifier = Modifier.padding(top = 14.dp),
                )
                Text("¡Bienvenida!", style = MaterialTheme.typography.headlineLarge)
            }
            BotonCircular(
                icono = Icons.Rounded.CalendarMonth,
                descripcion = "Ver mes",
                onClick = { avisar("El calendario del mes llega en la parte 2.7") },
            )
        }

        TiraSemana(
            semana = estado.semana,
            hoy = estado.hoy,
            seleccionado = estado.seleccionado,
            onElegir = viewModel::elegirDia,
        )

        TarjetaIlustrada(
            titulo = "Agrega a tu primera mascota",
            texto = "Chopper te ayuda a no olvidar ningún cuidado.",
            chip = "Primeros pasos",
            ilustracion = R.drawable.chopper_saludo,
            degradado = listOf(Pc.NaranjaClaro, Pc.Naranja),
            onClick = irAMascotas,
        )

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TituloSeccion(
                if (estado.seleccionado == estado.hoy) "Pendientes de hoy"
                else estado.seleccionado.format(FORMATO_DIA).replaceFirstChar { it.uppercase() },
            )
            EstadoVacio(
                ilustracion = R.drawable.chopper_comida,
                titulo = "Nada pendiente",
                texto = "Cuando crees recordatorios de comida, medicinas o baños, aparecerán aquí.",
                fondoBlob = listOf(Pc.SolClaro, Pc.Durazno),
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}
