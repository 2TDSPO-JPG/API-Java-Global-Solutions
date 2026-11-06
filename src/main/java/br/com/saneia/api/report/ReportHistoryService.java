package br.com.saneia.api.report;

import java.time.Clock;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Grava a trilha de auditoria dos chamados (participa da transação de quem chama). */
@Service
public class ReportHistoryService {

    private final ReportHistoryRepository history;
    private final Clock clock;

    public ReportHistoryService(ReportHistoryRepository history, Clock clock) {
        this.history = history;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Report report, ReportHistory.Type type, String actor, String from, String to, String message) {
        history.save(new ReportHistory(report, type, actor, from, to, message, false, clock.instant()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void internalNote(Report report, String actor, String message) {
        history.save(new ReportHistory(report, ReportHistory.Type.NOTE, actor, null, null, message, true, clock.instant()));
    }

    @Transactional(readOnly = true)
    public List<ReportHistory> of(Long reportId) {
        return history.findByReportIdOrderByCreatedAtAscIdAsc(reportId);
    }
}
