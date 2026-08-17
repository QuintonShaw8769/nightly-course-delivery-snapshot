package dev.infrai.edtech;

import java.time.LocalDate;
import java.util.List;

public final class SnapshotPolicy {
    public record CourseDelivery(
            String courseId,
            String learnerId,
            String educatorId,
            String deliveryStatus,
            LocalDate deadline,
            LocalDate completedOn) {
    }

    public record EducatorReport(
            String educatorId,
            long dueLearners,
            long overdueLearners) {
    }

    public List<EducatorReport> reportsFor(LocalDate snapshotDate, List<CourseDelivery> deliveries) {
        return deliveries.stream()
                .filter(delivery -> delivery.completedOn() == null)
                .filter(delivery -> !delivery.deadline().isAfter(snapshotDate))
                .collect(java.util.stream.Collectors.groupingBy(CourseDelivery::educatorId))
                .entrySet().stream()
                .map(entry -> new EducatorReport(
                        entry.getKey(),
                        entry.getValue().size(),
                        entry.getValue().stream()
                                .filter(delivery -> delivery.deadline().isBefore(snapshotDate))
                                .count()))
                .sorted(java.util.Comparator.comparing(EducatorReport::educatorId))
                .toList();
    }
}
