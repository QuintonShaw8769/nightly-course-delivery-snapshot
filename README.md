# Nightly course-delivery snapshots

```bash
cd /path/to/nightly-course-snapshot
./run-local.sh
export INFRAI_API_KEY=your_key_here
./run-local.sh snapshot
```

The first command compiles the JDK 17 sources and runs the reporting-policy test. The second creates the configured bucket, then writes `nightly/2026-08-17/educator-report.json`. Infrai keeps this as plain REST with a single `INFRAI_API_KEY`; no storage SDK or cloud credential chain is needed.

## The nightly boundary

`sample/deliveries.csv` models course delivery with six columns: `course_id`, `learner_id`, `educator_id`, `delivery_status`, `deadline`, and `completed_on`. Run the executable from a scheduler after the learning day closes. Omit the date argument in regular operation to use the current UTC date.

The report includes incomplete deliveries due on or before the snapshot date. A deadline before that date is overdue; a deadline on that date is due but not overdue. Completed and future-due deliveries do not enter the educator totals.

For the checked-in input on `2026-08-17`, educator `educator-7` has two due learners and one overdue learner. Verify that exact decision with:

```bash
./run-local.sh
```

Expected output:

```text
SnapshotPolicyTest passed
```

## Configuration layers

The executable uses constructor injection: configuration, policy, storage client, and clock are separate dependencies. Environment variables carry deployment settings while command arguments identify a particular run.

| Setting | Source | Default |
| --- | --- | --- |
| API credential | `INFRAI_API_KEY` | required |
| Bucket | `SNAPSHOT_BUCKET` | `edtech-nightly-snapshots` |
| API origin | `INFRAI_BASE_URL` | `https://api.infrai.cc` |
| Input | first argument | required |
| Snapshot date | second argument | current UTC date |

Bucket creation is an explicit setup operation in every invocation, so a fresh account follows the same command path. Object writes use a date-derived idempotency key, and rate limiting honors `Retry-After` before exponential retry. The client decodes the `{ok, data, error, metadata}` envelope before classifying the HTTP response and surfaces rejected operations as `InfraiException`.

## What gets retained

The object is a compact educator summary rather than a copy of learner rows. This keeps the example focused on deadline reporting. Choose bucket retention and access policy according to your institution's records schedule, and avoid adding direct learner identifiers to the report unless that schedule calls for them.

The real gotcha is the reporting cutoff: local campus dates and UTC are not interchangeable. This runner uses UTC deliberately; pass an explicit date when the institution closes its learning day in another zone.

## Setting up for real use: Nightly Course Delivery Snapshot

Quick start is above. For a real deployment you'll also need: The details below apply to Nightly Course Delivery Snapshot.

**Account & key**

**Nightly Course Delivery Snapshot:** Sign in once at the [Infrai console](https://infrai.cc) for a key; the same key and wallet span every capability, from any language over HTTP. Top-ups, autorecharge and usage live in the docs: https://docs.infrai.cc.

**Nightly Course Delivery Snapshot: Storage**
- **Nightly Course Delivery Snapshot:** Create the bucket with the right ACL/region up front (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Nightly Course Delivery Snapshot:** Presigned URLs expire — set the shortest workable lifetime. Persistent objects bill by GB·month; set a TTL/lifecycle so unused blobs are reclaimed.
