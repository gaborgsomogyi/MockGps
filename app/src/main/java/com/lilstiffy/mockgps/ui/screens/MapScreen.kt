package com.lilstiffy.mockgps.ui.screens

import android.view.MotionEvent
import android.widget.Toast
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lilstiffy.mockgps.MainActivity
import com.lilstiffy.mockgps.extensions.roundedShadow
import com.lilstiffy.mockgps.model.LatLng
import com.lilstiffy.mockgps.service.LocationHelper
import com.lilstiffy.mockgps.storage.StorageManager
import com.lilstiffy.mockgps.ui.components.FavoritesListComponent
import com.lilstiffy.mockgps.ui.components.FooterComponent
import com.lilstiffy.mockgps.ui.components.SearchComponent
import com.lilstiffy.mockgps.ui.screens.viewmodels.MapViewModel
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay

private val CARTO_DARK = object : OnlineTileSourceBase(
    "CartoDB Dark Matter", 1, 19, 256, ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/dark_all/",
        "https://b.basemaps.cartocdn.com/dark_all/",
        "https://c.basemaps.cartocdn.com/dark_all/",
        "https://d.basemaps.cartocdn.com/dark_all/"
    )
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        return baseUrl + MapTileIndex.getZoom(pMapTileIndex) + "/" +
                MapTileIndex.getX(pMapTileIndex) + "/" +
                MapTileIndex.getY(pMapTileIndex) + mImageFilenameEnding
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    mapViewModel: MapViewModel = viewModel(),
    activity: MainActivity,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isDarkTheme = isSystemInDarkTheme()

    val isMockingState = remember { mutableStateOf(false) }
    var isMocking by isMockingState

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showBottomSheet by remember { mutableStateOf(false) }

    val markerPosition by mapViewModel.markerPosition

    val mapView = remember {
        MapView(context).apply {
            setTileSource(if (isDarkTheme) CARTO_DARK else TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(
                org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER
            )
            controller.setZoom(15.0)
            controller.setCenter(GeoPoint(markerPosition.latitude, markerPosition.longitude))
        }
    }

    val marker = remember { Marker(mapView) }

    val clickOverlay = remember {
        object : Overlay() {
            override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
                if (!isMockingState.value) {
                    val geoPoint = mapView.projection.fromPixels(e.x.toInt(), e.y.toInt())
                    mapViewModel.updateMarkerPosition(
                        LatLng(geoPoint.latitude, geoPoint.longitude)
                    )
                }
                return true
            }
        }
    }

    LaunchedEffect(Unit) {
        mapView.overlays.add(clickOverlay)
        mapView.overlays.add(marker)
        LocationHelper.requestPermissions(activity)
        mapViewModel.updateMarkerPosition(mapViewModel.markerPosition.value)
    }

    LaunchedEffect(markerPosition) {
        marker.position = GeoPoint(markerPosition.latitude, markerPosition.longitude)
        mapView.invalidate()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    fun animateCamera() {
        mapView.controller.animateTo(GeoPoint(markerPosition.latitude, markerPosition.longitude))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { mapView }
        )

        Column(
            modifier = Modifier.statusBarsPadding()
        ) {
            SearchComponent(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .fillMaxHeight(0.075f)
                    .fillMaxWidth()
                    .padding(4.dp)
                    .roundedShadow(32.dp)
                    .zIndex(32f),
                onSearch = { searchTerm ->
                    if (isMocking) {
                        Toast.makeText(
                            activity,
                            "You can't search while mocking location",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@SearchComponent
                    }

                    LocationHelper.geocoding(searchTerm) { foundLatLng ->
                        foundLatLng?.let {
                            mapViewModel.updateMarkerPosition(it)
                            animateCamera()
                        }
                    }
                }
            )

            IconButton(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .align(Alignment.End),
                onClick = { showBottomSheet = true },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Blue, contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.List,
                    contentDescription = "show favorites"
                )
            }
        }

        FooterComponent(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(1f)
                .navigationBarsPadding()
                .padding(4.dp)
                .zIndex(32f)
                .roundedShadow(16.dp),
            address = mapViewModel.address.value,
            latLng = mapViewModel.markerPosition.value,
            isMocking = isMocking,
            isFavorite = mapViewModel.markerPositionIsFavorite.value,
            onStart = { isMocking = activity.toggleMocking() },
            onFavorite = { mapViewModel.toggleFavoriteForLocation() }
        )

        if (showBottomSheet) {
            FavoritesListComponent(
                onDismissRequest = { showBottomSheet = false },
                sheetState = sheetState,
                data = StorageManager.favorites,
                onEntryClicked = { clickedEntry ->
                    if (isMocking) {
                        Toast.makeText(
                            activity,
                            "You can't switch location while mocking",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@FavoritesListComponent
                    }
                    mapViewModel.updateMarkerPosition(clickedEntry.latLng)
                    scope.launch {
                        sheetState.hide()
                        showBottomSheet = false
                    }
                    animateCamera()
                }
            )
        }
    }
}
