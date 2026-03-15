package server.service;

import common.DailyStat;
import server.dao.DailyStatsDAO;
import server.dao.MapDAO;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AllCitiesReportGenerator implements ReportGenerator {
    @Override
    public List<DailyStat> generate(LocalDate from, LocalDate to, Integer cityId) {
        // Include every city that has at least one approved map (not only cities with activity in the date range)
        List<Integer> cityIdsWithMaps = MapDAO.getCityIdsWithApprovedMaps();
        if (cityIdsWithMaps.isEmpty()) {
            List<DailyStat> global = DailyStatsDAO.getGlobalStatsPerDay(from, to);
            int sumOne = 0, sumSubs = 0, sumRen = 0, sumViews = 0, sumDown = 0;
            for (DailyStat s : global) {
                sumOne += s.getOneTimePurchases();
                sumSubs += s.getSubscriptions();
                sumRen += s.getRenewals();
                sumViews += s.getViews();
                sumDown += s.getDownloads();
            }
            List<DailyStat> one = new ArrayList<>();
            one.add(new DailyStat(from, 0, MapDAO.countAllMaps(), sumOne, sumSubs, sumRen, sumViews, sumDown));
            return one;
        }
        Map<Integer, DailyStat> activityByCity = new HashMap<>();
        for (DailyStat s : DailyStatsDAO.getPerCityTotals(from, to)) {
            activityByCity.put(s.getCityId(), s);
        }
        List<DailyStat> result = new ArrayList<>();
        for (Integer cid : cityIdsWithMaps) {
            int mapsCount = MapDAO.countMapsForCity(cid);
            DailyStat activity = activityByCity.get(cid);
            if (activity != null) {
                activity.setMapsCount(mapsCount);
                result.add(activity);
            } else {
                result.add(new DailyStat(from, cid, mapsCount, 0, 0, 0, 0, 0));
            }
        }
        return result;
    }
}
