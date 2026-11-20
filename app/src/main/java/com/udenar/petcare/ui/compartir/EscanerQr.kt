package com.udenar.petcare.ui.compartir

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.udenar.petcare.domain.compartir.codigoValido
import com.udenar.petcare.domain.mascotas.QrMascota
import com.udenar.petcare.ui.componentes.BotonCircular
import com.udenar.petcare.ui.componentes.BotonPrincipal
import com.udenar.petcare.ui.componentes.Seccion
import com.udenar.petcare.ui.theme.Pc

/**
 * Escáner de QR de Google Play services (RF-19): no pide permiso de cámara a PetCare
 * porque la cámara la maneja Google y solo devuelve el texto leído.
 */
fun escanearQr(contexto: Context, onTexto: (String) -> Unit, onError: (String) -> Unit) {
    val opciones = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
    GmsBarcodeScanning.getClient(contexto, opciones).startScan()
        .addOnSuccessListener { codigo -> codigo.rawValue?.let(onTexto) ?: onError("No se pudo leer el código") }
        .addOnFailureListener { onError("El escáner no está disponible: ${it.localizedMessage ?: "intenta de nuevo"}") }
}

/** Ficha pública leída de un QR de PetCare, o el texto crudo si el QR es de otra cosa. */
@Composable
fun ResultadoQrScreen(texto: String, onVolver: () -> Unit, onQuieroCuidar: (String) -> Unit) {
    val ficha = QrMascota.leer(texto)
    Column(
        Modifier
            .fillMaxSize()
            .background(Pc.Crema)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BotonCircular(Icons.AutoMirrored.Rounded.ArrowBack, "Volver", onClick = onVolver)
            Text(if (ficha != null) "Ficha de ${ficha.nombre ?: "la mascota"}" else "Código leído", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 16.dp))
        }
        if (ficha == null) {
            Text("Este QR no es de PetCare. Su contenido es:", style = MaterialTheme.typography.bodyMedium, color = Pc.TintaSuave)
            Text(
                texto,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(Pc.Blanco)
                    .padding(16.dp)
                    .testTag("texto_crudo"),
            )
        } else {
            Seccion("Datos compartidos por su dueño") {
                ficha.valores.filterKeys { it != "Codigo" }.forEach { (k, v) ->
                    Column(Modifier.testTag("dato_qr_$k")) {
                        Text(k, style = MaterialTheme.typography.labelMedium, color = Pc.TintaSuave)
                        Text(v, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            val codigo = ficha.codigo
            if (codigo != null && codigoValido(codigo)) {
                Text(
                    "Su dueño compartió el código para cuidarla. Puedes pedirlo; solo tendrás acceso cuando te apruebe.",
                    style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave,
                )
                BotonPrincipal("Quiero cuidar esta mascota", onClick = { onQuieroCuidar(codigo) })
            }
        }
    }
}
