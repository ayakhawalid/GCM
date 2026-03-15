package common;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * DTO representing daily statistics for a city or aggregated data.
 */
public class DailyStat implements Serializable {
    private static final long serialVersionUID = 1L;

    private LocalDate date;
    private int cityId;
    private int mapsCount;
    private int oneTimePurchases;
    private int subscriptions;
    private int renewals;
    private int views;
    private int downloads;

    public DailyStat() {
    }

    public DailyStat(LocalDate date, int cityId, int mapsCount, int oneTimePurchases, int subscriptions, int renewals,
            int views, int downloads) {
        this.date = date;
        this.cityId = cityId;
        this.mapsCount = mapsCount;
        this.oneTimePurchases = oneTimePurchases;
        this.subscriptions = subscriptions;
        this.renewals = renewals;
        this.views = views;
        this.downloads = downloads;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public int getCityId() {
        return cityId;
    }

    public void setCityId(int cityId) {
        this.cityId = cityId;
    }

    public int getMapsCount() {
        return mapsCount;
    }

    public void setMapsCount(int mapsCount) {
        this.mapsCount = mapsCount;
    }

    public int getOneTimePurchases() {
        return oneTimePurchases;
    }

    public void setOneTimePurchases(int oneTimePurchases) {
        this.oneTimePurchases = oneTimePurchases;
    }

    public int getSubscriptions() {
        return subscriptions;
    }

    public void setSubscriptions(int subscriptions) {
        this.subscriptions = subscriptions;
    }

    public int getRenewals() {
        return renewals;
    }

    public void setRenewals(int renewals) {
        this.renewals = renewals;
    }

    public int getViews() {
        return views;
    }

    public void setViews(int views) {
        this.views = views;
    }

    public int getDownloads() {
        return downloads;
    }

    public void setDownloads(int downloads) {
        this.downloads = downloads;
    }

    @Override
    public String toString() {
        return "DailyStat{" +
                "date=" + date +
                ", cityId=" + cityId +
                ", mapsCount=" + mapsCount +
                ", oneTimePurchases=" + oneTimePurchases +
                ", subscriptions=" + subscriptions +
                ", renewals=" + renewals +
                ", views=" + views +
                ", downloads=" + downloads +
                '}';
    }
}
