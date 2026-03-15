package server.handler;

import common.DailyStat;
import common.MessageType;
import common.Request;
import common.Response;
import common.dto.ReportRequest;
import server.service.AllCitiesReportGenerator;
import server.service.CityReportGenerator;
import server.service.ReportGenerator;

import java.util.List;

public class ReportHandler {

    public static boolean canHandle(MessageType type) {
        return type == MessageType.GET_ACTIVITY_REPORT;
    }

    public static Response handle(Request request) {
        if (request.getType() != MessageType.GET_ACTIVITY_REPORT) {
            return Response.error(request, Response.ERR_INTERNAL, "Invalid message type for ReportHandler");
        }

        if (!(request.getPayload() instanceof ReportRequest)) {
            return Response.error(request, Response.ERR_VALIDATION, "Invalid payload. Expected ReportRequest.");
        }

        ReportRequest reportReq = (ReportRequest) request.getPayload();

        // Strategy Selection
        ReportGenerator generator;
        if (reportReq.getCityId() != null && reportReq.getCityId() > 0) {
            generator = new CityReportGenerator();
        } else {
            generator = new AllCitiesReportGenerator();
        }

        try {
            List<DailyStat> stats = generator.generate(reportReq.getFromDate(), reportReq.getToDate(),
                    reportReq.getCityId());
            System.out.println("[Report] Sending response with " + (stats != null ? stats.size() : 0) + " stats");
            return Response.success(request, stats);
        } catch (Exception e) {
            e.printStackTrace();
            return Response.error(request, Response.ERR_DATABASE, "Error generating report: " + e.getMessage());
        }
    }
}
