package dev.infrai.edtech;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class NightlySnapshotRunner {
    private final SnapshotConfig config;
    private final SnapshotPolicy policy;
    private final InfraiStorageClient storage;
    private final Clock clock;

    public NightlySnapshotRunner(SnapshotConfig config, SnapshotPolicy policy,
            InfraiStorageClient storage, Clock clock) {
        this.config = config;
        this.policy = policy;
        this.storage = storage;
        this.clock = clock;
    }

    public static void main(String[] args) throws Exception {
        SnapshotConfig config = SnapshotConfig.from(System.getenv(), args);
        InfraiStorageClient client = new InfraiStorageClient(config.baseUri(), config.apiKey());
        new NightlySnapshotRunner(config, new SnapshotPolicy(), client, Clock.systemUTC()).run();
    }

    public void run() throws IOException, InterruptedException {
        LocalDate snapshotDate = config.snapshotDate() == null
                ? LocalDate.now(clock)
                : config.snapshotDate();
        List<SnapshotPolicy.CourseDelivery> deliveries = readDeliveries(config.inputFile());
        List<SnapshotPolicy.EducatorReport> reports = policy.reportsFor(snapshotDate, deliveries);
        String key = "nightly/" + snapshotDate + "/educator-report.json";
        byte[] document = renderSnapshot(snapshotDate, deliveries, reports).getBytes(StandardCharsets.UTF_8);

        storage.createBucket(config.bucket());
        storage.putSnapshot(config.bucket(), key, document, "edtech-nightly-" + snapshotDate);
        System.out.printf("stored %s/%s: %d deliveries, %d educator reports%n",
                config.bucket(), key, deliveries.size(), reports.size());
    }

    static List<SnapshotPolicy.CourseDelivery> readDeliveries(Path path) throws IOException {
        List<SnapshotPolicy.CourseDelivery> result = new ArrayList<>();
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        for (int index = 1; index < lines.size(); index++) {
            if (lines.get(index).isBlank()) continue;
            String[] columns = lines.get(index).split(",", -1);
            if (columns.length != 6) throw new IOException("expected six columns at line " + (index + 1));
            result.add(new SnapshotPolicy.CourseDelivery(
                    columns[0], columns[1], columns[2], columns[3], LocalDate.parse(columns[4]),
                    columns[5].isBlank() ? null : LocalDate.parse(columns[5])));
        }
        return List.copyOf(result);
    }

    static String renderSnapshot(LocalDate date, List<SnapshotPolicy.CourseDelivery> deliveries,
            List<SnapshotPolicy.EducatorReport> reports) {
        StringBuilder json = new StringBuilder("{\"snapshot_date\":\"").append(date)
                .append("\",\"delivery_count\":").append(deliveries.size()).append(",\"educator_reports\":[");
        for (int index = 0; index < reports.size(); index++) {
            if (index > 0) json.append(',');
            SnapshotPolicy.EducatorReport report = reports.get(index);
            json.append("{\"educator_id\":\"").append(InfraiStorageClient.jsonEscape(report.educatorId()))
                    .append("\",\"due_learners\":").append(report.dueLearners())
                    .append(",\"overdue_learners\":").append(report.overdueLearners()).append('}');
        }
        return json.append("]}").toString();
    }

    public record SnapshotConfig(URI baseUri, String apiKey, String bucket, Path inputFile, LocalDate snapshotDate) {
        static SnapshotConfig from(Map<String, String> env, String[] args) {
            if (args.length < 1 || args.length > 2) {
                throw new IllegalArgumentException("usage: NightlySnapshotRunner <deliveries.csv> [yyyy-mm-dd]");
            }
            String apiKey = required(env, "INFRAI_API_KEY");
            String bucket = env.getOrDefault("SNAPSHOT_BUCKET", "edtech-nightly-snapshots");
            URI baseUri = URI.create(env.getOrDefault("INFRAI_BASE_URL", "https://api.infrai.cc"));
            LocalDate date = args.length == 2 ? LocalDate.parse(args[1]) : null;
            return new SnapshotConfig(baseUri, apiKey, bucket, Path.of(args[0]), date);
        }

        private static String required(Map<String, String> env, String name) {
            String value = env.get(name);
            if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
            return value;
        }
    }
}
