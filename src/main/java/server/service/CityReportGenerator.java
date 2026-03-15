package server.service;

import common.DailyStat;
import server.dao.DailyStatsDAO;
import server.dao.MapDAO;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class CityReportGenerator implements ReportGenerator {
    @Override
    public List<DailyStat> generate(LocalDate from, LocalDate to, Integer cityId) {
        if (cityId == null) {
            throw new IllegalArgumentException("City ID required for City Report");
        }
        List<DailyStat> daily = DailyStatsDAO.getStats(from, to, cityId);
        int sumOneTime = 0, sumSubs = 0, sumRenewals = 0, sumViews = 0, sumDownloads = 0;
        for (DailyStat s : daily) {
            sumOneTime += s.getOneTimePurchases();
            sumSubs += s.getSubscriptions();
            sumRenewals += s.getRenewals();
            sumViews += s.getViews();
            sumDownloads += s.getDownloads();
        }
        int mapsCount = MapDAO.countMapsForCity(cityId);
        List<DailyStat> result = new ArrayList<>();
        result.add(new DailyStat(from, cityId, mapsCount, sumOneTime, sumSubs, sumRenewals, sumViews, sumDownloads));
        return result;
    }
}
