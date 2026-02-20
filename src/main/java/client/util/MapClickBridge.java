package client.util;

import javafx.application.Platform;

import java.util.function.BiConsumer;

/**
 * Bridge for JavaScript (Leaflet map) to call back into Java when user clicks on the map.
 * Must be public so the WebEngine can invoke it.
 */
public class MapClickBridge {

    private BiConsumer<Double, Double> onMapClickConsumer;
    private Runnable onMapReadyConsumer;

    public void setOnMapClickConsumer(BiConsumer<Double, Double> consumer) {
        this.onMapClickConsumer = consumer;
    }

    public void setOnMapReadyConsumer(Runnable consumer) {
        this.onMapReadyConsumer = consumer;
    }

    /**
     * Called from JavaScript when user clicks on the map. Runs the consumer on the JavaFX thread.
     */
    public void onMapClick(double lat, double lng) {
        if (onMapClickConsumer == null) return;
        double latVal = lat;
        double lngVal = lng;
        Platform.runLater(() -> onMapClickConsumer.accept(latVal, lngVal));
    }

    /**
     * Same as onMapClick – for JS bridge name {@code javafx.poiAdded(lat, lon)}.
     */
    public void poiAdded(double lat, double lng) {
        onMapClick(lat, lng);
    }

    /**
     * Called from JavaScript when the map has been created and is ready. Runs the consumer on the JavaFX thread.
     */
    public void onMapReady() {
        if (onMapReadyConsumer == null) return;
        Platform.runLater(onMapReadyConsumer);
    }
}
