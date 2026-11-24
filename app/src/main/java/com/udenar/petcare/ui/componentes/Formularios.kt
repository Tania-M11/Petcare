package com.udenar.petcare.ui.componentes

import com.udenar.petcare.domain.modelo.Icono
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.unit.dp
import com.udenar.petcare.ui.theme.Pc

/** Campo de texto redondeado y blanco, con su error debajo. */
@Composable
fun CampoTexto(
    valor: String,
    onCambio: (String) -> Unit,
    etiqueta: String,
    modifier: Modifier = Modifier,
    icono: ImageVector? = null,
    error: String? = null,
    teclado: KeyboardType = KeyboardType.Text,
    accion: ImeAction = ImeAction.Next,
    onAccion: () -> Unit = {},
    esClave: Boolean = false,
    habilitado: Boolean = true,
    lineas: Int = 1,
) {
    var verClave by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = valor,
        onValueChange = onCambio,
        label = { Text(etiqueta) },
        leadingIcon = icono?.let { { Icon(it, contentDescription = null, tint = Pc.TintaSuave) } },
        trailingIcon = if (esClave) {
            {
                IconButton(onClick = { verClave = !verClave }) {
                    Icon(
                        if (verClave) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (verClave) "Ocultar contraseña" else "Mostrar contraseña",
                        tint = Pc.TintaSuave,
                    )
                }
            }
        } else null,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = lineas == 1,
        minLines = lineas,
        enabled = habilitado,
        visualTransformation = if (esClave && !verClave) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = teclado, imeAction = accion),
        keyboardActions = KeyboardActions(onAny = { onAccion() }),
        shape = RoundedCornerShape(20.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Pc.Blanco,
            unfocusedContainerColor = Pc.Blanco,
            disabledContainerColor = Pc.Blanco,
            errorContainerColor = Pc.Blanco,
            focusedBorderColor = Pc.Naranja,
            unfocusedBorderColor = Pc.Linea,
            focusedLabelColor = Pc.Naranja,
            cursorColor = Pc.Naranja,
        ),
        textStyle = MaterialTheme.typography.bodyLarge,
        modifier = modifier.fillMaxWidth(),
    )
}

/** Botón principal naranja en píldora; muestra un indicador mientras trabaja. */
@Composable
fun BotonPrincipal(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cargando: Boolean = false,
    habilitado: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = habilitado && !cargando,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = Pc.Naranja,
            contentColor = Pc.Blanco,
            disabledContainerColor = Pc.NaranjaClaro,
            disabledContentColor = Pc.Blanco,
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (cargando) {
                CircularProgressIndicator(color = Pc.Blanco, strokeWidth = 2.5.dp, modifier = Modifier.size(24.dp))
            } else {
                Text(texto, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** Botón secundario blanco en píldora. */
@Composable
fun BotonSecundario(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier, icono: Icono? = null) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, Pc.Linea),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Pc.Blanco, contentColor = Pc.Tinta),
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        icono?.let { Icono3D(it, tam = 22.dp, modifier = Modifier.padding(end = 8.dp)) }
        Text(texto, style = MaterialTheme.typography.titleMedium)
    }
}

/** Opción en píldora que se puede seleccionar (especie, sexo, tamaño…). */
@Composable
fun ChipOpcion(
    texto: String,
    seleccionado: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icono: Icono? = null,
) {
    androidx.compose.foundation.layout.Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (seleccionado) Pc.Durazno else Pc.Blanco)
            .border(1.5.dp, if (seleccionado) Pc.Naranja else Pc.Linea, RoundedCornerShape(50))
            .selectable(selected = seleccionado, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
    ) {
        icono?.let { Icono3D(it, tam = 18.dp) }
        Text(texto, style = MaterialTheme.typography.labelLarge, color = if (seleccionado) Pc.Tinta else Pc.TintaSuave)
    }
}

/** Bloque blanco redondeado con título, para agrupar campos de un formulario o una ficha. */
@Composable
fun Seccion(titulo: String, modifier: Modifier = Modifier, contenido: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Pc.Blanco)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(titulo, style = MaterialTheme.typography.titleMedium)
        contenido()
    }
}
