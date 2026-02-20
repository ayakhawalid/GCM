package client.boundary;

import org.jxmapviewer.viewer.TileFactoryInfo;

/**
 * OpenStreetMap tile info with correct URL format: https://tile.openstreetmap.org/{z}/{x}/{y}.png
 * (z = zoom 0–19, not inverted). JXMapViewer2's OSMTileFactoryInfo uses invZoom which produces wrong URLs.
 */
public class OSMCorrectTileFactoryInfo extends TileFactoryInfo {

    private static final int MAX_ZOOM = 19;
    private static final String BASE_URL = "https://tile.openstreetmap.org";

    public OSMCorrectTileFactoryInfo() {
        super("OpenStreetMap",
                0, MAX_ZOOM, MAX_ZOOM,
                256, true, true,
                BASE_URL,
                "x", "y", "z");
    }

    @Override
    public String getTileUrl(int x, int y, int zoom) {
        return BASE_URL + "/" + zoom + "/" + x + "/" + y + ".png";
    }
}
