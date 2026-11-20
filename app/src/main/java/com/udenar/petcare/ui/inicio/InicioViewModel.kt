package com.udenar.petcare.ui.inicio

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/** Por ahora solo muestra la fecha de hoy; en la parte 1.11 se convierte en la pantalla "Hoy". */
@HiltViewModel
class InicioViewModel @Inject constructor(reloj: Clock) : ViewModel() {

    val fechaDeHoy: String = LocalDate.now(reloj)
        .format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es-CO")))
        .replaceFirstChar { it.titlecase(Locale.forLanguageTag("es-CO")) }
}
