package common.dto;

import java.io.Serializable;

/**
 * Data Transfer Object for checking 10% renewal discount eligibility.
 */
public class DiscountCheckRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private int cityId;
    private int months;

    public DiscountCheckRequest(int cityId, int months) {
        this.cityId = cityId;
        this.months = months;
    }

    public int getCityId() {
        return cityId;
    }

    public int getMonths() {
        return months;
    }
}
