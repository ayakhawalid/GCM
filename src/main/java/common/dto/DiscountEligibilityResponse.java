package common.dto;

import java.io.Serializable;

/**
 * Response DTO for checking 10% renewal discount eligibility.
 */
public class DiscountEligibilityResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private final boolean eligible;
    private final int cityId;
    private final int months;

    public DiscountEligibilityResponse(boolean eligible, int cityId, int months) {
        this.eligible = eligible;
        this.cityId = cityId;
        this.months = months;
    }

    public boolean isEligible() {
        return eligible;
    }

    public int getCityId() {
        return cityId;
    }

    public int getMonths() {
        return months;
    }
}

