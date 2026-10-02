package org.opentreenap.mobile.map

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.clustering.ClusterItem
import org.opentreenap.mobile.model.TreeMarker

data class TreeClusterItem(
    val tree: TreeMarker
) : ClusterItem {
    override fun getPosition(): LatLng =
        LatLng(tree.latitude, tree.longitude)

    override fun getTitle(): String =
        tree.title

    override fun getSnippet(): String? =
        tree.snippet

    override fun getZIndex(): Float? =
        if (tree.isMonumental) 3f else 2f
}
