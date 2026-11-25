package com.udenar.petcare.ui.acceso

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udenar.petcare.R
import com.udenar.petcare.domain.cuenta.Campo
import com.udenar.petcare.ui.componentes.BlobDecorativo
import com.udenar.petcare.ui.componentes.grano
import com.udenar.petcare.ui.componentes.HuellasFlotantes
import com.udenar.petcare.ui.componentes.BotonCircular
import com.udenar.petcare.ui.componentes.BotonPrincipal
import com.udenar.petcare.ui.componentes.CampoTexto
import com.udenar.petcare.ui.theme.Pc

/**
 * Marco común de acceso: nube de color con el logo y Michi arriba, el título grande debajo y
 * el formulario desplazable sobre el teclado.
 */
@Composable
private fun MarcoFormulario(
    titulo: String,
    subtitulo: String,
    @DrawableRes ilustracion: Int,
    onVolver: () -> Unit,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Pc.Crema)
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(250.dp)
                .clip(com.udenar.petcare.ui.componentes.formaNube(ondas = 4, alto = 0.07f))
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Pc.LavandaClara, Pc.Durazno)))
                .grano(0.3f),
        ) {
            HuellasFlotantes()
            Row(Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                BotonCircular(Icons.AutoMirrored.Rounded.ArrowBack, "Volver", onClick = onVolver)
                Image(painterResource(com.udenar.petcare.R.drawable.logo_petcare), contentDescription = "PetCare", modifier = Modifier.padding(start = 14.dp).height(28.dp))
            }
            Image(
                painterResource(ilustracion),
                contentDescription = null,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp).height(150.dp),
            )
        }
        Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column {
                Text(titulo, style = MaterialTheme.typography.headlineLarge)
                Text(subtitulo, style = MaterialTheme.typography.bodyMedium, color = Pc.TintaSuave)
            }
            contenido()
        }
    }
}

@Composable
private fun ErrorGeneral(texto: String?) {
    if (texto != null) {
        Text(
            texto,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(Pc.Durazno)
                .padding(14.dp)
                .testTag("error_general"),
        )
    }
}

@Composable
fun RegistroScreen(
    onVolver: () -> Unit,
    onIrAEntrar: () -> Unit,
    viewModel: RegistroViewModel = hiltViewModel(),
) {
    val f by viewModel.form.collectAsStateWithLifecycle()
    var verPolitica by remember { mutableStateOf(false) }

    MarcoFormulario("Crea tu cuenta", "Una sola cuenta para todo", R.drawable.gato_dormido, onVolver) {
        CampoTexto(f.nombre, viewModel::nombre, "Nombre", icono = Icons.Rounded.Person, error = f.errores[Campo.NOMBRE], habilitado = !f.cargando)
        CampoTexto(
            f.correo, viewModel::correo, "Correo", icono = Icons.Rounded.Mail, error = f.errores[Campo.CORREO],
            teclado = KeyboardType.Email, habilitado = !f.cargando,
        )
        CampoTexto(
            f.clave, viewModel::clave, "Contraseña (mínimo 8)", icono = Icons.Rounded.Lock, error = f.errores[Campo.CLAVE],
            teclado = KeyboardType.Password, accion = ImeAction.Done, esClave = true, habilitado = !f.cargando,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = f.aceptoPrivacidad,
                onCheckedChange = viewModel::privacidad,
                colors = CheckboxDefaults.colors(checkedColor = Pc.Naranja, uncheckedColor = Pc.TintaSuave),
                modifier = Modifier.testTag("casilla_privacidad"),
            )
            Text(
                buildAnnotatedString {
                    append("Acepto la ")
                    withStyle(SpanStyle(color = Pc.Naranja, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)) {
                        append("política de privacidad")
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable { verPolitica = true },
            )
        }
        f.errores[Campo.PRIVACIDAD]?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 14.dp))
        }

        ErrorGeneral(f.errorGeneral)
        Aviso(f.aviso)
        BotonPrincipal("Crear cuenta", onClick = viewModel::registrar, cargando = f.cargando)
        Text(
            "¿Ya tienes cuenta? Inicia sesión",
            style = MaterialTheme.typography.labelLarge,
            color = Pc.Naranja,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = onIrAEntrar)
                .padding(10.dp),
        )
    }

    if (verPolitica) DialogoPrivacidad(onCerrar = { verPolitica = false })
}

@Composable
fun InicioSesionScreen(
    onVolver: () -> Unit,
    onIrARegistro: () -> Unit,
    onOlvide: () -> Unit,
    viewModel: InicioSesionViewModel = hiltViewModel(),
) {
    val f by viewModel.form.collectAsStateWithLifecycle()

    MarcoFormulario("Hola de nuevo", "Tus mascotas te esperan", R.drawable.gato_dormido, onVolver) {
        CampoTexto(
            f.correo, viewModel::correo, "Correo", icono = Icons.Rounded.Mail, error = f.errores[Campo.CORREO],
            teclado = KeyboardType.Email, habilitado = !f.cargando,
        )
        CampoTexto(
            f.clave, viewModel::clave, "Contraseña", icono = Icons.Rounded.Lock, error = f.errores[Campo.CLAVE],
            teclado = KeyboardType.Password, accion = ImeAction.Done, onAccion = viewModel::entrar, esClave = true,
            habilitado = !f.cargando,
        )
        ErrorGeneral(f.errorGeneral)
        BotonPrincipal("Entrar", onClick = viewModel::entrar, cargando = f.cargando)
        Text(
            "¿Olvidaste tu contraseña?",
            style = MaterialTheme.typography.labelLarge,
            color = Pc.TintaSuave,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = onOlvide)
                .padding(10.dp)
                .testTag("olvide_clave"),
        )
        Text(
            "¿No tienes cuenta? Crea una",
            style = MaterialTheme.typography.labelLarge,
            color = Pc.Naranja,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = onIrARegistro)
                .padding(10.dp),
        )
    }
}

@Composable
private fun Aviso(texto: String?) {
    if (texto != null) {
        Text(
            texto,
            style = MaterialTheme.typography.bodyMedium,
            color = Pc.Tinta,
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(Pc.MentaClara)
                .padding(14.dp)
                .testTag("aviso_acceso"),
        )
    }
}

/** Recuperar contraseña: envía un enlace al correo (5.2). */
@Composable
fun RecuperarClaveScreen(onVolver: () -> Unit, viewModel: RecuperarClaveViewModel = hiltViewModel()) {
    val f by viewModel.form.collectAsStateWithLifecycle()
    MarcoFormulario("¿Olvidaste tu contraseña?", "Te enviamos un enlace", R.drawable.gato_leyendo, onVolver) {
        Text("Escribe el correo de tu cuenta. El enlace abre PetCare para que crees una contraseña nueva.", style = MaterialTheme.typography.bodyMedium, color = Pc.TintaSuave)
        CampoTexto(
            f.correo, viewModel::correo, "Correo", icono = Icons.Rounded.Mail, error = f.errores[Campo.CORREO],
            teclado = KeyboardType.Email, accion = ImeAction.Done, onAccion = viewModel::enviar, habilitado = !f.cargando,
            modifier = Modifier.testTag("correo_recuperar"),
        )
        ErrorGeneral(f.errorGeneral)
        Aviso(f.aviso)
        BotonPrincipal("Enviar enlace", onClick = viewModel::enviar, cargando = f.cargando, modifier = Modifier.testTag("enviar_enlace"))
    }
}

/** Se abre con el enlace de recuperación: la sesión ya está iniciada y falta la contraseña nueva. */
@Composable
fun NuevaClaveScreen(viewModel: NuevaClaveViewModel = hiltViewModel()) {
    val f by viewModel.form.collectAsStateWithLifecycle()
    MarcoFormulario("Nueva contraseña", "Elige una que recuerdes", R.drawable.gato_saludo, viewModel::cancelar) {
        CampoTexto(
            f.clave, viewModel::clave, "Contraseña nueva (mínimo 8)", icono = Icons.Rounded.Lock, error = f.errores[Campo.CLAVE],
            teclado = KeyboardType.Password, esClave = true, habilitado = !f.cargando,
        )
        CampoTexto(
            f.clave2, viewModel::clave2, "Repítela", icono = Icons.Rounded.Lock,
            teclado = KeyboardType.Password, accion = ImeAction.Done, onAccion = viewModel::guardar, esClave = true, habilitado = !f.cargando,
        )
        ErrorGeneral(f.errorGeneral)
        BotonPrincipal("Guardar contraseña", onClick = viewModel::guardar, cargando = f.cargando)
    }
}
