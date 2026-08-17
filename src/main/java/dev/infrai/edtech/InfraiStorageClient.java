package dev.infrai.edtech;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

public final class InfraiStorageClient {
    private final HttpClient http;
    private final URI baseUri;
    private final String apiKey;

    public InfraiStorageClient(URI baseUri, String apiKey) {
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.baseUri = baseUri;
        this.apiKey = apiKey;
    }

    public void createBucket(String name) throws IOException, InterruptedException {
        call("POST", "/v1/storage/bucket/create", "{\"name\":\"" + jsonEscape(name) + "\"}");
    }

    public void putSnapshot(String bucket, String key, byte[] content, String idempotencyKey)
            throws IOException, InterruptedException {
        String body = "{\"data_base64\":\"" + Base64.getEncoder().encodeToString(content)
                + "\",\"content_type\":\"application/json\",\"idempotency_key\":\""
                + jsonEscape(idempotencyKey) + "\"}";
        call("PUT", "/v1/storage/object/put/" + pathSegment(bucket) + "/" + pathSegment(key), body);
    }

    private Map<String, Object> call(String method, String path, String body)
            throws IOException, InterruptedException {
        for (int attempt = 0; attempt < 5; attempt++) {
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> envelope = Json.parseObject(response.body());
            if (response.statusCode() == 429 && attempt < 4) {
                Thread.sleep(retryDelayMillis(response, attempt));
                continue;
            }
            if (!Boolean.TRUE.equals(envelope.get("ok"))) {
                Object error = envelope.get("error");
                throw new InfraiException(response.statusCode(), error == null ? "request rejected" : error.toString());
            }
            if (response.statusCode() >= 500) {
                throw new IOException("Infrai transport status " + response.statusCode());
            }
            Object data = envelope.get("data");
            return data instanceof Map<?, ?> map ? castMap(map) : Map.of();
        }
        throw new IOException("retry budget exhausted");
    }

    private static long retryDelayMillis(HttpResponse<?> response, int attempt) {
        return response.headers().firstValue("Retry-After")
                .map(value -> {
                    try {
                        return Math.max(1L, Long.parseLong(value)) * 1_000L;
                    } catch (NumberFormatException ignored) {
                        return 250L << attempt;
                    }
                })
                .orElse(250L << attempt);
    }

    private static String pathSegment(String value) {
        StringBuilder encoded = new StringBuilder();
        for (byte current : value.getBytes(StandardCharsets.UTF_8)) {
            int unsigned = current & 0xff;
            if ((unsigned >= 'a' && unsigned <= 'z') || (unsigned >= 'A' && unsigned <= 'Z')
                    || (unsigned >= '0' && unsigned <= '9') || unsigned == '-' || unsigned == '_'
                    || unsigned == '.' || unsigned == '~') {
                encoded.append((char) unsigned);
            } else {
                encoded.append('%').append(String.format("%02X", unsigned));
            }
        }
        return encoded.toString();
    }

    static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    public static final class InfraiException extends IOException {
        private final int statusCode;

        InfraiException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }

        public int statusCode() {
            return statusCode;
        }
    }

    private static final class Json {
        private final String input;
        private int position;

        private Json(String input) {
            this.input = input;
        }

        static Map<String, Object> parseObject(String input) throws IOException {
            Object parsed = new Json(input).value();
            if (parsed instanceof Map<?, ?> map) {
                return castMap(map);
            }
            throw new IOException("response envelope is not an object");
        }

        private Object value() throws IOException {
            whitespace();
            if (position >= input.length()) throw new IOException("empty JSON response");
            return switch (input.charAt(position)) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object() throws IOException {
            position++;
            Map<String, Object> result = new LinkedHashMap<>();
            whitespace();
            if (take('}')) return result;
            do {
                whitespace();
                String key = string();
                whitespace();
                require(':');
                result.put(key, value());
                whitespace();
            } while (take(','));
            require('}');
            return result;
        }

        private java.util.List<Object> array() throws IOException {
            position++;
            java.util.List<Object> result = new java.util.ArrayList<>();
            whitespace();
            if (take(']')) return result;
            do {
                result.add(value());
                whitespace();
            } while (take(','));
            require(']');
            return result;
        }

        private String string() throws IOException {
            require('"');
            StringBuilder result = new StringBuilder();
            while (position < input.length()) {
                char current = input.charAt(position++);
                if (current == '"') return result.toString();
                if (current != '\\') {
                    result.append(current);
                    continue;
                }
                if (position >= input.length()) throw new IOException("bad JSON escape");
                char escaped = input.charAt(position++);
                if (escaped == 'u') {
                    if (position + 4 > input.length()) throw new IOException("bad Unicode escape");
                    result.append((char) Integer.parseInt(input.substring(position, position + 4), 16));
                    position += 4;
                } else {
                    int index = "\"\\/bfnrt".indexOf(escaped);
                    if (index < 0) throw new IOException("bad JSON escape");
                    result.append("\"\\/\b\f\n\r\t".charAt(index));
                }
            }
            throw new IOException("unterminated JSON string");
        }

        private Object number() throws IOException {
            int start = position;
            while (position < input.length() && "-+0123456789.eE".indexOf(input.charAt(position)) >= 0) position++;
            try {
                return Double.valueOf(input.substring(start, position));
            } catch (NumberFormatException exception) {
                throw new IOException("invalid JSON value", exception);
            }
        }

        private Object literal(String text, Object value) throws IOException {
            if (!input.startsWith(text, position)) throw new IOException("invalid JSON literal");
            position += text.length();
            return value;
        }

        private void whitespace() {
            while (position < input.length() && Character.isWhitespace(input.charAt(position))) position++;
        }

        private boolean take(char expected) {
            if (position < input.length() && input.charAt(position) == expected) {
                position++;
                return true;
            }
            return false;
        }

        private void require(char expected) throws IOException {
            if (!take(expected)) throw new IOException("expected '" + expected + "' in JSON response");
        }
    }
}
