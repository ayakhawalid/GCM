package server.service;

import common.DailyStat;
import java.time.LocalDate;
import java.util.List;

public interface ReportGenerator {
    /**
     * Generate report stats for the given criteria.
     */
    List<DailyStat> generate(LocalDate from, LocalDate to, Integer cityId);
}
