package org.opentreenap.mobile.map

import android.content.Context
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.MarkerOptions
import com.google.maps.android.clustering.Cluster
import com.google.maps.android.clustering.ClusterManager
import com.google.maps.android.clustering.view.DefaultClusterRenderer
import org.opentreenap.mobile.model.TreeMarker

class OtnClusterRenderer(
    context: Context,
    map: GoogleMap,
    clusterManager: ClusterManager<TreeClusterItem>,
    private val treeIconProvider: (TreeMarker) -> BitmapDescriptor,
    private val clusterIconProvider: (count: Int, hasMonumental: Boolean) -> BitmapDescriptor
) : DefaultClusterRenderer<TreeClusterItem>(
    context,
    map,
    clusterManager
) {
    override fun onBeforeClusterItemRendered(
        item: TreeClusterItem,
        markerOptions: MarkerOptions
    ) {
        markerOptions
            .icon(treeIconProvider(item.tree))
            .anchor(0.5f, 0.5f)
            .zIndex(if (item.tree.isMonumental) 3f else 2f)
    }

    override fun onBeforeClusterRendered(
        cluster: Cluster<TreeClusterItem>,
        markerOptions: MarkerOptions
    ) {
        val hasMonumental =
            cluster.items.any { it.tree.isMonumental }

        markerOptions
            .icon(
                clusterIconProvider(
                    cluster.size,
                    hasMonumental
                )
            )
            .anchor(0.5f, 0.5f)
            .zIndex(if (hasMonumental) 2f else 1f)
    }

    override fun shouldRenderAsCluster(
        cluster: Cluster<TreeClusterItem>
    ): Boolean = cluster.size > 1
}
