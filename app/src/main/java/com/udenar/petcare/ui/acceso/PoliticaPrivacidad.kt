package com.udenar.petcare.ui.acceso

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.udenar.petcare.ui.theme.Pc

/** Política de tratamiento de datos (Ley 1581 de 2012), en lenguaje sencillo. */
private val SECCIONES = listOf(
    "Qué datos guardamos" to
        "Tu nombre, correo y, si los agregas, teléfono y WhatsApp. De tus mascotas: su ficha, cuidados, salud, documentos y paseos que registres.",
    "Para qué los usamos" to
        "Solo para que la app funcione: recordatorios, historial, compartir el cuidado con las personas que tú autorices y conectar con veterinarios o prestadores que tú elijas.",
    "Con quién se comparten" to
        "Con nadie más sin tu decisión. Los cuidadores ven lo que su rol permite; un prestador solo ve lo que marques al pedir un servicio y deja de verlo al terminar.",
    "Servicios externos" to
        "Los mapas usan OpenFreeMap. Si diste permiso de ubicación, para sugerir el mejor momento del paseo se consulta el clima en Open-Meteo con tu ubicación aproximada (unos 1 km), sin tu nombre ni datos de tus mascotas. Las fotos y etiquetas se procesan en tu teléfono.",
    "Tus derechos" to
        "Puedes conocer, actualizar, corregir y pedir que se borren tus datos en cualquier momento desde tu Perfil.",
    "Importante" to
        "PetCare es un proyecto académico de la Universidad de Nariño. No da diagnósticos ni recomienda dosis o alimentos: solo guarda y te recuerda lo que tú o tu veterinario escriben.",
)

@Composable
fun DialogoPrivacidad(onCerrar: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCerrar,
        containerColor = Pc.Crema,
        confirmButton = { TextButton(onClick = onCerrar) { Text("Entendido") } },
        title = { Text("Política de privacidad", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SECCIONES.forEach { (titulo, texto) ->
                    Column {
                        Text(titulo, style = MaterialTheme.typography.titleSmall)
                        Text(texto, style = MaterialTheme.typography.bodyMedium, color = Pc.TintaSuave)
                    }
                }
            }
        },
    )
}
