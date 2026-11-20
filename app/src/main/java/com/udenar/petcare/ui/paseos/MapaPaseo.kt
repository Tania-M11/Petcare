package com.udenar.petcare.ui.paseos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.udenar.petcare.domain.paseos.Coordenada
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** Estilo de OpenFreeMap (teselas vectoriales de OpenStreetMap, sin clave). Incluye la atribución. */
const val ESTILO_MAPA = "https://tiles.openfreemap.org/styles/liberty"

/** Pasto, si todavía no hay ninguna posición. */
private val CENTRO_INICIAL = LatLng(1.2136, -77.2811)

private const val FUENTE_RUTA = "ruta"
private const val FUENTE_POSICION = "posicion"

/**
 * Mapa del paseo (MapLibre): dibuja la ruta y la posición actual.
 * [seguir] = la cámara acompaña la última posición (paseo en curso); si no, encuadra toda la ruta.
 */
@Composable
fun MapaPaseo(ruta: List<Coordenada>, posicion: Coordenada?, seguir: Boolean, modifier: Modifier = Modifier) {
    val contexto = LocalContext.current
    val ciclo = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        // Idempotente: asegura el motor aunque la aplicación no lo haya iniciado (p. ej. en pruebas)
        org.maplibre.android.MapLibre.getInstance(contexto.applicationContext)
        MapView(contexto).apply { onCreate(null) }
    }
    var mapa by remember { mutableStateOf<MapLibreMap?>(null) }
    var estilo by remember { mutableStateOf<Style?>(null) }

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
        ciclo.addObserver(observador)
        onDispose {
            ciclo.removeObserver(observador)
            if (ciclo.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (ciclo.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            m.cameraPosition = CameraPosition.Builder().target(CENTRO_INICIAL).zoom(14.0).build()
            m.uiSettings.isRotateGesturesEnabled = false
            m.setStyle(Style.Builder().fromUri(ESTILO_MAPA)) { s ->
                s.addSource(GeoJsonSource(FUENTE_RUTA))
                s.addSource(GeoJsonSource(FUENTE_POSICION))
                s.addLayer(
                    LineLayer("capa_ruta", FUENTE_RUTA).withProperties(
                        lineColor("#F08A4B"), lineWidth(6f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
                    ),
                )
                s.addLayer(
                    CircleLayer("capa_posicion", FUENTE_POSICION).withProperties(
                        circleRadius(8f), circleColor("#8F78D6"), circleStrokeColor("#FFFFFF"), circleStrokeWidth(3f),
                    ),
                )
                estilo = s
            }
            mapa = m
        }
    }

    // Cada cambio de ruta o posición actualiza las capas y la cámara
    LaunchedEffect(estilo, ruta, posicion, seguir) {
        val s = estilo ?: return@LaunchedEffect
        val m = mapa ?: return@LaunchedEffect
        (s.getSource(FUENTE_RUTA) as? GeoJsonSource)?.setGeoJson(
            FeatureCollection.fromFeatures(
                if (ruta.size > 1) listOf(Feature.fromGeometry(LineString.fromLngLats(ruta.map { Point.fromLngLat(it.lon, it.lat) }))) else emptyList(),
            ),
        )
        (s.getSource(FUENTE_POSICION) as? GeoJsonSource)?.setGeoJson(
            FeatureCollection.fromFeatures(listOfNotNull(posicion?.let { Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat)) })),
        )
        when {
            seguir && posicion != null -> m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(posicion.lat, posicion.lon), 16.5), 600)
            !seguir && ruta.size > 1 -> {
                val limites = LatLngBounds.Builder().includes(ruta.map { LatLng(it.lat, it.lon) }).build()
                m.moveCamera(CameraUpdateFactory.newLatLngBounds(limites, 80))
            }
            !seguir && ruta.size == 1 -> m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(ruta[0].lat, ruta[0].lon), 16.0))
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}
