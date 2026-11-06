package br.com.saneia.api.report;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportHistoryRepository extends JpaRepository<ReportHistory, Long> {

    List<ReportHistory> findByReportIdOrderByCreatedAtAscIdAsc(Long reportId);
}
