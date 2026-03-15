package common.dto;

import java.io.Serializable;
import java.util.Map;

/**
 * Data Transfer Object for city pricing info.
 */
public class CityPriceInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    private int cityId;
    private String cityName;
    private double oneTimePrice;

    // Key: number of months, Value: price
    private Map<Integer, Double> subscriptionPrices;

    public CityPriceInfo(int cityId, String cityName, double oneTimePrice, Map<Integer, Double> subscriptionPrices) {
        this.cityId = cityId;
        this.cityName = cityName;
        this.oneTimePrice = oneTimePrice;
        this.subscriptionPrices = subscriptionPrices;
    }

    public int getCityId() {
        return cityId;
    }

    public String getCityName() {
        return cityName;
    }

    public double getOneTimePrice() {
        return oneTimePrice;
    }

    public Map<Integer, Double> getSubscriptionPrices() {
        return subscriptionPrices;
    }
}
