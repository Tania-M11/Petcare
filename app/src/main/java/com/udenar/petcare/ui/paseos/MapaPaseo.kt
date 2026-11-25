package com.udenar.petcare.ui.paseos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.udenar.petcare.domain.paseos.Coordenada
import com.udenar.petcare.ui.mapas.MapaLibre
import com.udenar.petcare.ui.mapas.latLng
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
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

private const val FUENTE_RUTA = "ruta"
private const val FUENTE_POSICION = "posicion"

/**
 * Mapa del paseo (MapLibre): dibuja la ruta y la posición actual.
 * [seguir] = la cámara acompaña la última posición (paseo en curso); si no, encuadra toda la ruta.
 */
@Composable
fun MapaPaseo(ruta: List<Coordenada>, posicion: Coordenada?, seguir: Boolean, modifier: Modifier = Modifier, interactivo: Boolean = true) {
    var listo by remember { mutableStateOf<Pair<MapLibreMap, Style>?>(null) }
    MapaLibre(modifier, centro = posicion ?: ruta.lastOrNull() ?: com.udenar.petcare.ui.mapas.CENTRO_INICIAL, interactivo = interactivo) { m, s ->
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
        listo = m to s
    }

    // Cada cambio de ruta o posición actualiza las capas y la cámara
    LaunchedEffect(listo, ruta, posicion, seguir) {
        val (m, s) = listo ?: return@LaunchedEffect
        (s.getSource(FUENTE_RUTA) as? GeoJsonSource)?.setGeoJson(
            FeatureCollection.fromFeatures(
                if (ruta.size > 1) listOf(Feature.fromGeometry(LineString.fromLngLats(ruta.map { Point.fromLngLat(it.lon, it.lat) }))) else emptyList(),
            ),
        )
        (s.getSource(FUENTE_POSICION) as? GeoJsonSource)?.setGeoJson(
            FeatureCollection.fromFeatures(listOfNotNull(posicion?.let { Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat)) })),
        )
        when {
            seguir && posicion != null -> m.animateCamera(CameraUpdateFactory.newLatLngZoom(posicion.latLng(), 16.5), 600)
            !seguir && ruta.size > 1 -> m.moveCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(ruta.map { it.latLng() }).build(), 80))
            !seguir && ruta.size == 1 -> m.moveCamera(CameraUpdateFactory.newLatLngZoom(ruta[0].latLng(), 16.0))
        }
    }
}
