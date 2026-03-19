package common.dto;

import java.io.Serializable;

/**
 * Response DTO for checking 10% renewal discount eligibility.
 */
public class DiscountEligibilityResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    // Whether the user has an active subscription for the same city + months.
    // Controls the button label ("Renew subscription...").
    private final boolean renewalEligible;

    // Whether the subscription is expiring within the discount window (<= 3 days).
    // Controls the 10% discount message + checkout price.
    private final boolean discountEligible;
    private final int cityId;
    private final int months;

    public DiscountEligibilityResponse(boolean renewalEligible, boolean discountEligible, int cityId, int months) {
        this.renewalEligible = renewalEligible;
        this.discountEligible = discountEligible;
        this.cityId = cityId;
        this.months = months;
    }

    public boolean isRenewalEligible() {
        return renewalEligible;
    }

    public boolean isDiscountEligible() {
        return discountEligible;
    }

    public int getCityId() {
        return cityId;
    }

    public int getMonths() {
        return months;
    }
}

