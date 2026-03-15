package common.dto;

import java.io.Serializable;
import java.time.LocalDate;

public class ReportRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private LocalDate fromDate;
    private LocalDate toDate;
    private Integer cityId; // Null means all cities

    public ReportRequest() {
    }

    public ReportRequest(LocalDate fromDate, LocalDate toDate, Integer cityId) {
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.cityId = cityId;
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public void setFromDate(LocalDate fromDate) {
        this.fromDate = fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public void setToDate(LocalDate toDate) {
        this.toDate = toDate;
    }

    public Integer getCityId() {
        return cityId;
    }

    public void setCityId(Integer cityId) {
        this.cityId = cityId;
    }
}
