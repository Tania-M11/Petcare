package com.udenar.petcare

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.udenar.petcare.data.alarmas.Notificador
import com.udenar.petcare.data.avisos.Apertura
import com.udenar.petcare.data.avisos.AperturaAvisos
import com.udenar.petcare.data.avisos.DestinoAviso
import com.udenar.petcare.data.cuenta.RecuperacionClave
import com.udenar.petcare.domain.modelo.ModoUso
import com.udenar.petcare.ui.raiz.PetcareApp
import com.udenar.petcare.ui.theme.PetcareTheme
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.handleDeeplinks
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var supabase: SupabaseClient
    @Inject lateinit var apertura: AperturaAvisos
    @Inject lateinit var recuperacion: RecuperacionClave

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        leer(intent)
        setContent {
            PetcareTheme {
                PetcareApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        leer(intent)
    }

    private fun leer(intent: Intent?) {
        intent ?: return
        // Aviso tocado: abre el modo y la pestaña de la solicitud (RF-29)
        val modo = intent.getStringExtra(Notificador.EXTRA_MODO)
        if (modo != null) {
            apertura.abrir(
                Apertura(
                    ModoUso.entries.firstOrNull { it.name == modo } ?: ModoUso.PERSONAL,
                    DestinoAviso.entries.firstOrNull { it.name == intent.getStringExtra(Notificador.EXTRA_DESTINO) } ?: DestinoAviso.HOY,
                ),
            )
            intent.removeExtra(Notificador.EXTRA_MODO)
        }
        // Enlace del correo (confirmar cuenta o recuperar contraseña): petcare://auth...
        val datos = intent.data
        if (datos?.scheme == "petcare" && datos.host == "auth") {
            if (datos.path.orEmpty().contains("recuperar") || datos.toString().contains("type=recovery")) recuperacion.pendiente.value = true
            runCatching { supabase.handleDeeplinks(intent) }
        }
    }
}
