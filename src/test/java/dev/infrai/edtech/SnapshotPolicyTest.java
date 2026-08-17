package dev.infrai.edtech;

import java.time.LocalDate;
import java.util.List;

public final class SnapshotPolicyTest {
    public static void main(String[] args) {
        LocalDate snapshotDate = LocalDate.parse("2026-08-17");
        List<SnapshotPolicy.CourseDelivery> deliveries = List.of(
                new SnapshotPolicy.CourseDelivery("course-a", "learner-1", "educator-9", "ASSIGNED",
                        LocalDate.parse("2026-08-16"), null),
                new SnapshotPolicy.CourseDelivery("course-a", "learner-2", "educator-9", "ASSIGNED",
                        snapshotDate, null),
                new SnapshotPolicy.CourseDelivery("course-b", "learner-3", "educator-9", "COMPLETED",
                        LocalDate.parse("2026-08-15"), LocalDate.parse("2026-08-14")),
                new SnapshotPolicy.CourseDelivery("course-c", "learner-4", "educator-4", "ASSIGNED",
                        LocalDate.parse("2026-08-18"), null));

        List<SnapshotPolicy.EducatorReport> actual = new SnapshotPolicy().reportsFor(snapshotDate, deliveries);
        List<SnapshotPolicy.EducatorReport> expected = List.of(
                new SnapshotPolicy.EducatorReport("educator-9", 2, 1));
        if (!actual.equals(expected)) {
            throw new AssertionError("expected " + expected + " but got " + actual);
        }
        System.out.println("SnapshotPolicyTest passed");
    }
}
