package com.udenar.petcare.ui.mapas

import android.annotation.SuppressLint
import android.graphics.RectF
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.udenar.petcare.domain.paseos.Coordenada
import com.udenar.petcare.ui.theme.Pc
import com.udenar.petcare.ui.componentes.vector
import androidx.compose.ui.graphics.asAndroidPath
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/** Estilo de OpenFreeMap (teselas vectoriales de OpenStreetMap, sin clave). */
const val ESTILO_MAPA = "https://tiles.openfreemap.org/styles/liberty"
const val ATRIBUCION_MAPA = "© OpenFreeMap © OpenMapTiles · Datos de © colaboradores de OpenStreetMap"

/** Pasto, si todavía no hay ninguna posición. */
val CENTRO_INICIAL = Coordenada(1.2136, -77.2811)

fun Coordenada.latLng() = LatLng(lat, lon)

/** MapView de MapLibre atado al ciclo de vida de la pantalla. */
@Composable
fun rememberMapaLibre(): MapView {
    val contexto = LocalContext.current
    val ciclo = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        // Idempotente: asegura el motor aunque la aplicación no lo haya iniciado (p. ej. en pruebas)
        MapLibre.getInstance(contexto.applicationContext)
        MapView(contexto).apply { onCreate(null) }
    }
    DisposableEffect(ciclo, mapView) {
        val observador = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        // Si la pantalla ya está activa al entrar (p. ej. en un diálogo), se arranca de una vez
        if (ciclo.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (ciclo.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        ciclo.addObserver(observador)
        onDispose {
            ciclo.removeObserver(observador)
            if (ciclo.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (ciclo.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }
    return mapView
}

/** Prepara el mapa y su estilo; [alListo] recibe ambos una sola vez. */
@Composable
fun MapaLibre(
    modifier: Modifier = Modifier,
    centro: Coordenada = CENTRO_INICIAL,
    zoom: Double = 14.0,
    interactivo: Boolean = true,
    alListo: (MapLibreMap, Style) -> Unit,
) {
    val mapView = rememberMapaLibre()
    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            m.cameraPosition = CameraPosition.Builder().target(centro.latLng()).zoom(zoom).build()
            m.uiSettings.isRotateGesturesEnabled = false
            m.uiSettings.isTiltGesturesEnabled = false
            // Mapa de vista previa (p. ej. en una tarjeta): no roba el desplazamiento de la pantalla
            if (!interactivo) m.uiSettings.setAllGesturesEnabled(false)
            m.setStyle(Style.Builder().fromUri(ESTILO_MAPA)) { s -> alListo(m, s) }
        }
    }
    AndroidView(factory = { mapView }, modifier = modifier)
}

/**
 * Un punto del directorio: [destacado] = verificado o recibiendo (borde de color); [icono] = la
 * categoría, dibujada en blanco dentro de la burbuja del marcador (Fase 6.8).
 */
data class PuntoMapa(
    val id: String,
    val posicion: Coordenada,
    val destacado: Boolean,
    val icono: com.udenar.petcare.domain.modelo.Icono = com.udenar.petcare.domain.modelo.Icono.HUELLAS,
    /** Color propio del marcador (p. ej. veterinarias sin cuenta); null = destacado/normal. */
    val color: Int? = null,
)

/** Burbuja del color del lugar con borde blanco, el ícono en blanco y una punta hacia el lugar. */
internal fun imagenMarcador(contexto: android.content.Context, icono: com.udenar.petcare.domain.modelo.Icono, borde: Int): android.graphics.Bitmap {
    val d = contexto.resources.displayMetrics.density
    val lado = (40 * d).toInt()
    val punta = (8 * d).toInt()
    val bmp = android.graphics.Bitmap.createBitmap(lado, lado + punta, android.graphics.Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    val r = lado / 2f
    val blanco = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    val relleno = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = borde }
    // Punta y círculo blancos (borde), luego el color adentro
    c.drawPath(android.graphics.Path().apply {
        moveTo(r - punta, lado - punta * 1.2f); lineTo(r + punta, lado - punta * 1.2f); lineTo(r, (lado + punta).toFloat()); close()
    }, blanco)
    c.drawCircle(r, r, r, blanco)
    c.drawCircle(r, r, r - 3 * d, relleno)
    // El ícono vectorial (24×24) en blanco, escalado al centro
    val v = icono.vector()
    val trazo = android.graphics.Path()
    fun recorrer(g: androidx.compose.ui.graphics.vector.VectorGroup) {
        g.forEach { n ->
            when (n) {
                is androidx.compose.ui.graphics.vector.VectorPath -> trazo.addPath(
                    androidx.compose.ui.graphics.vector.PathParser().addPathNodes(n.pathData).toPath().asAndroidPath(),
                )
                is androidx.compose.ui.graphics.vector.VectorGroup -> recorrer(n)
            }
        }
    }
    recorrer(v.root)
    val tamIcono = lado * 0.5f
    val escala = tamIcono / v.viewportWidth
    trazo.transform(android.graphics.Matrix().apply { setScale(escala, escala); postTranslate(r - tamIcono / 2, r - tamIcono / 2) })
    c.drawPath(trazo, blanco)
    return bmp
}

private fun claveMarcador(p: PuntoMapa) = "marcador_${p.icono.name}_${p.destacado}_${p.color}"

private const val FUENTE_PUNTOS = "puntos"
private const val FUENTE_YO = "yo"

/** Mapa con marcadores que se pueden tocar (directorios). Encuadra todos los puntos. */
@Composable
fun MapaMarcadores(
    puntos: List<PuntoMapa>,
    onElegir: (String) -> Unit,
    modifier: Modifier = Modifier,
    colorDestacado: Int = 0xFF5B89BA.toInt(),
    colorNormal: Int = 0xFFA98EE0.toInt(),
    /** Dónde está la persona ("Cerca de mí"): punto azul y entra en el encuadre. */
    miUbicacion: Coordenada? = null,
) {
    var listo by remember { mutableStateOf<Pair<MapLibreMap, Style>?>(null) }
    val elegir by rememberUpdatedState(onElegir)
    val contexto = LocalContext.current
    MapaLibre(modifier, centro = puntos.firstOrNull()?.posicion ?: CENTRO_INICIAL, zoom = 13.0) { m, s ->
        s.addSource(GeoJsonSource(FUENTE_PUNTOS))
        // La burbuja con el ícono (ya no hay punto de color debajo: se veía como una mancha)
        s.addLayer(
            org.maplibre.android.style.layers.SymbolLayer("capa_puntos", FUENTE_PUNTOS).withProperties(
                org.maplibre.android.style.layers.PropertyFactory.iconImage("{imagen}"),
                org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap(true),
                org.maplibre.android.style.layers.PropertyFactory.iconAnchor(org.maplibre.android.style.layers.Property.ICON_ANCHOR_BOTTOM),
            ),
        )
        m.addOnMapClickListener { p ->
            val pantalla = m.projection.toScreenLocation(p)
            val zona = RectF(pantalla.x - 30, pantalla.y - 30, pantalla.x + 30, pantalla.y + 30)
            val id = m.queryRenderedFeatures(zona, "capa_puntos").firstOrNull()?.getStringProperty("id")
            if (id != null) elegir(id)
            id != null
        }
        // Punto azul de "tú estás aquí"
        s.addSource(GeoJsonSource(FUENTE_YO))
        s.addLayer(
            CircleLayer("capa_yo_halo", FUENTE_YO).withProperties(circleRadius(16f), circleColor("#5B89BA"), org.maplibre.android.style.layers.PropertyFactory.circleOpacity(0.25f)),
        )
        s.addLayer(
            CircleLayer("capa_yo", FUENTE_YO).withProperties(circleRadius(7f), circleColor("#2F6FB5"), circleStrokeColor("#FFFFFF"), circleStrokeWidth(3f)),
        )
        listo = m to s
    }
    LaunchedEffect(listo, puntos, miUbicacion) {
        val (m, s) = listo ?: return@LaunchedEffect
        (s.getSource(FUENTE_YO) as? GeoJsonSource)?.setGeoJson(
            FeatureCollection.fromFeatures(listOfNotNull(miUbicacion?.let { Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat)) })),
        )
        // Una imagen por combinación de ícono y estado, antes de usarla en los puntos
        puntos.distinctBy(::claveMarcador).forEach { p ->
            if (s.getImage(claveMarcador(p)) == null) s.addImage(claveMarcador(p), imagenMarcador(contexto, p.icono, p.color ?: if (p.destacado) colorDestacado else colorNormal))
        }
        (s.getSource(FUENTE_PUNTOS) as? GeoJsonSource)?.setGeoJson(
            FeatureCollection.fromFeatures(
                puntos.map { p ->
                    Feature.fromGeometry(Point.fromLngLat(p.posicion.lon, p.posicion.lat)).apply {
                        addStringProperty("id", p.id)
                        addBooleanProperty("destacado", p.destacado)
                        addStringProperty("imagen", claveMarcador(p))
                    }
                },
            ),
        )
        // Encuadra los lugares y, si se conoce, la ubicación de la persona
        val todos = puntos.map { it.posicion } + listOfNotNull(miUbicacion)
        when {
            todos.size > 1 -> m.moveCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(todos.map { it.latLng() }).build(), 120))
            todos.size == 1 -> m.moveCamera(CameraUpdateFactory.newLatLngZoom(todos[0].latLng(), 15.0))
        }
    }
}

/**
 * Mapa con un pin fijo en el centro: se mueve el mapa hasta dejar el pin sobre el lugar.
 * [onCentro] recibe el centro cada vez que el mapa se detiene; [irA] mueve la cámara desde fuera.
 */
@Composable
fun SelectorUbicacion(inicial: Coordenada?, irA: Coordenada?, onCentro: (Coordenada) -> Unit, modifier: Modifier = Modifier) {
    var mapa by remember { mutableStateOf<MapLibreMap?>(null) }
    val avisar by rememberUpdatedState(onCentro)
    Box(modifier) {
        MapaLibre(Modifier.matchParentSize(), centro = inicial ?: CENTRO_INICIAL, zoom = if (inicial != null) 16.0 else 13.0) { m, _ ->
            m.addOnCameraIdleListener { m.cameraPosition.target?.let { avisar(Coordenada(it.latitude, it.longitude)) } }
            mapa = m
        }
        // La punta del pin queda en el centro exacto del mapa
        Icon(
            Icons.Rounded.Place, contentDescription = "Ubicación elegida", tint = Pc.Naranja,
            modifier = Modifier.align(Alignment.Center).size(48.dp).offset(y = (-22).dp),
        )
    }
    LaunchedEffect(mapa, irA) {
        val m = mapa ?: return@LaunchedEffect
        irA?.let { m.animateCamera(CameraUpdateFactory.newLatLngZoom(it.latLng(), 16.0), 700) }
    }
}

/**
 * Ubicación actual (requiere permiso concedido). Pide una lectura nueva con alta precisión y,
 * si no llega, usa la última conocida. null = sin ubicación (p. ej. el GPS está apagado).
 */
@android.annotation.SuppressLint("MissingPermission")
fun ubicacionActual(contexto: android.content.Context, alObtener: (Coordenada?) -> Unit) {
    val cliente = LocationServices.getFusedLocationProviderClient(contexto)
    val token = com.google.android.gms.tasks.CancellationTokenSource()
    cliente.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token)
        .addOnSuccessListener { l ->
            if (l != null) alObtener(Coordenada(l.latitude, l.longitude))
            else cliente.lastLocation.addOnSuccessListener { u -> alObtener(u?.let { Coordenada(it.latitude, it.longitude) }) }.addOnFailureListener { alObtener(null) }
        }
        .addOnFailureListener { alObtener(null) }
}

/** ¿Está encendida la ubicación del teléfono? (sin ella no hay lectura aunque haya permiso). */
fun ubicacionEncendida(contexto: android.content.Context): Boolean =
    (contexto.getSystemService(android.content.Context.LOCATION_SERVICE) as? android.location.LocationManager)
        ?.let { androidx.core.location.LocationManagerCompat.isLocationEnabled(it) } ?: false
