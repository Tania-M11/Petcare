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
    alListo: (MapLibreMap, Style) -> Unit,
) {
    val mapView = rememberMapaLibre()
    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            m.cameraPosition = CameraPosition.Builder().target(centro.latLng()).zoom(zoom).build()
            m.uiSettings.isRotateGesturesEnabled = false
            m.uiSettings.isTiltGesturesEnabled = false
            m.setStyle(Style.Builder().fromUri(ESTILO_MAPA)) { s -> alListo(m, s) }
        }
    }
    AndroidView(factory = { mapView }, modifier = modifier)
}

/** Un punto del directorio: [destacado] = verificado (marcador azul); los demás, gris lavanda. */
data class PuntoMapa(val id: String, val posicion: Coordenada, val destacado: Boolean)

private const val FUENTE_PUNTOS = "puntos"

/** Mapa con marcadores que se pueden tocar (directorios). Encuadra todos los puntos. */
@Composable
fun MapaMarcadores(puntos: List<PuntoMapa>, onElegir: (String) -> Unit, modifier: Modifier = Modifier) {
    var listo by remember { mutableStateOf<Pair<MapLibreMap, Style>?>(null) }
    val elegir by rememberUpdatedState(onElegir)
    MapaLibre(modifier, centro = puntos.firstOrNull()?.posicion ?: CENTRO_INICIAL, zoom = 13.0) { m, s ->
        s.addSource(GeoJsonSource(FUENTE_PUNTOS))
        s.addLayer(
            CircleLayer("capa_puntos", FUENTE_PUNTOS).withProperties(
                circleRadius(10f),
                circleColor(Expression.switchCase(Expression.get("destacado"), Expression.color(0xFF5B89BA.toInt()), Expression.color(0xFFA98EE0.toInt()))),
                circleStrokeColor("#FFFFFF"), circleStrokeWidth(3f),
            ),
        )
        m.addOnMapClickListener { p ->
            val pantalla = m.projection.toScreenLocation(p)
            val zona = RectF(pantalla.x - 30, pantalla.y - 30, pantalla.x + 30, pantalla.y + 30)
            val id = m.queryRenderedFeatures(zona, "capa_puntos").firstOrNull()?.getStringProperty("id")
            if (id != null) elegir(id)
            id != null
        }
        listo = m to s
    }
    LaunchedEffect(listo, puntos) {
        val (m, s) = listo ?: return@LaunchedEffect
        (s.getSource(FUENTE_PUNTOS) as? GeoJsonSource)?.setGeoJson(
            FeatureCollection.fromFeatures(
                puntos.map { p ->
                    Feature.fromGeometry(Point.fromLngLat(p.posicion.lon, p.posicion.lat)).apply {
                        addStringProperty("id", p.id)
                        addBooleanProperty("destacado", p.destacado)
                    }
                },
            ),
        )
        when {
            puntos.size > 1 -> m.moveCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(puntos.map { it.posicion.latLng() }).build(), 120))
            puntos.size == 1 -> m.moveCamera(CameraUpdateFactory.newLatLngZoom(puntos[0].posicion.latLng(), 15.0))
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

/** Ubicación actual aproximada (requiere permiso de ubicación ya concedido). */
@SuppressLint("MissingPermission")
fun ubicacionActual(contexto: android.content.Context, alObtener: (Coordenada?) -> Unit) {
    LocationServices.getFusedLocationProviderClient(contexto)
        .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
        .addOnSuccessListener { l -> alObtener(l?.let { Coordenada(it.latitude, it.longitude) }) }
        .addOnFailureListener { alObtener(null) }
}
