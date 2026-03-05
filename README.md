# Qanal CLI

Command-line interface for the Qanal high-speed file transfer platform. Sends and receives files over QUIC, manages transfers, and handles API key and billing operations. Built with picocli, Java 21.

---

## Table of Contents

- [Installation](#installation)
- [Initial Setup](#initial-setup)
- [Commands](#commands)
  - [config](#config)
  - [send](#send)
  - [receive](#receive)
  - [list](#list)
  - [status](#status)
  - [pause](#pause)
  - [resume](#resume)
  - [cancel](#cancel)
  - [me](#me)
- [How Sending Works](#how-sending-works)
- [How Receiving Works](#how-receiving-works)
- [Config File](#config-file)
- [Exit Codes](#exit-codes)
- [Building from Source](#building-from-source)
- [Troubleshooting](#troubleshooting)

---

## Installation

### Option A — Download the pre-built JAR

```bash
# Download
curl -L https://releases.qanal.io/cli/qanal-cli-latest.jar -o /usr/local/lib/qanal.jar

# Create an alias (add to ~/.bashrc or ~/.zshrc)
echo 'alias qanal="java -jar /usr/local/lib/qanal.jar"' >> ~/.bashrc
source ~/.bashrc
```

### Option B — Build from source

```bash
cd CLI
./gradlew shadowJar
# Produces: CLI/build/libs/qanal-cli-*.jar

# Alias
alias qanal="java -jar $(pwd)/build/libs/qanal-cli-*.jar"
```

### Requirements

| Requirement | Version |
|-------------|---------|
| Java | 21+ |
| Network | UDP port 4433 and 4434 must be reachable from the client machine |

Verify Java version:
```bash
java -version
# openjdk version "21.x.x" ...
```

---

## Initial Setup

Before running any transfer commands you must configure your API key and the server URL. These are stored in `~/.qanal/config.json` and loaded automatically by every command.

```bash
# 1. Set the server URL (get this from your Qanal administrator)
qanal config set-url https://api.yourdomain.com

# 2. Set your API key (get this from your administrator)
qanal config set-key qnl_a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6

# 3. Verify setup
qanal config show
# Config file : /home/user/.qanal/config.json
# Server URL  : https://api.yourdomain.com
# API Key     : qnl_****o5p6

# 4. Check your account
qanal me
#   Name:        Acme Corp
#   Plan:        FREE
#   Usage:       0 B this month
#   Member since: 3 months ago
```

---

## Commands

### config

Manages CLI configuration stored in `~/.qanal/config.json`.

#### `config set-key <api-key>`

Saves an API key to the config file.

```bash
qanal config set-key qnl_a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6
# ✓ API key saved to /home/user/.qanal/config.json
```

API keys are issued by your Qanal administrator or via the Organization API. The key format is `qnl_<32 hex characters>` (36 characters total).

#### `config set-url <url>`

Saves the Control Plane base URL.

```bash
qanal config set-url https://api.yourdomain.com
qanal config set-url http://localhost:8080   # local development
```

#### `config show`

Prints the current configuration. The API key is masked for security.

```bash
qanal config show
# Config file : /home/user/.qanal/config.json
# Server URL  : https://api.yourdomain.com
# API Key     : qnl_a1b2****o5p6
```

---

### send

Uploads a file to the Qanal network. The Control Plane plans the transfer, assigns a relay node, and the CLI streams all chunks over QUIC in parallel.

```
qanal send <file> [options]
```

| Option | Short | Default | Description |
|--------|-------|---------|-------------|
| `--to <region>` | `-t` | _(auto)_ | Target region for the recipient to download from (e.g. `eu-west-1`) |
| `--from <region>` | `-f` | _(auto)_ | Source region label for routing decisions (e.g. `us-east-1`) |
| `--streams <n>` | `-s` | `8` | Number of parallel QUIC streams (more = faster on high-bandwidth links) |
| `--api-key <key>` | — | _(config)_ | Override API key from config for this command only |
| `--url <url>` | — | _(config)_ | Override server URL for this command only |

#### Examples

```bash
# Basic send — auto-selects relay
qanal send report.pdf

# Send to a specific region
qanal send dataset.tar.gz --to eu-west-1

# Specify both source and target regions, use 32 parallel streams
qanal send bigfile.zip --from us-east-1 --to ap-southeast-1 --streams 32

# Use a different API key for this command only
qanal send file.zip --api-key qnl_other_key
```

#### Output

```
ℹ Computing checksum for 10.5 GB file...
ℹ Initiating transfer...
✓ Transfer ID: 019500ab-1234-7abc-def0-123456789abc
  Relay  : relay.us-east-1.qanal.io:4433
  Chunks : 42

dataset.tar.gz  [=================================>] 100%  8.3 Gbps
```

**Share the Transfer ID with the recipient** — they need it to download the file.

#### What happens internally

1. xxHash64 of the entire file is computed (8 MB streaming buffer, no heap OOM)
2. `POST /api/v1/transfers` registers the transfer; the server returns chunk boundaries and the relay address
3. A virtual thread per chunk streams data over independent QUIC streams (up to `--streams` concurrent)
4. A background virtual thread subscribes to the SSE progress stream and renders the progress bar
5. When all chunks are sent, the connection is closed and the CLI waits up to 5 seconds for the final SSE event

---

### receive

Downloads a completed transfer from the egress DataPlane node.

```
qanal receive <transfer-id> [options]
```

| Option | Short | Default | Description |
|--------|-------|---------|-------------|
| `--output <path>` | `-o` | _(current directory)_ | Output file path or directory. If a directory is given, the original filename is used |

#### Examples

```bash
# Download to current directory (uses original filename)
qanal receive 019500ab-1234-7abc-def0-123456789abc

# Download to a specific directory
qanal receive 019500ab-1234-7abc-def0-123456789abc --output /data/received/

# Download to a specific file path
qanal receive 019500ab-1234-7abc-def0-123456789abc --output /data/received/renamed.tar.gz
```

#### Output

```
ℹ Downloading  dataset.tar.gz  (10.5 GB)
ℹ From         relay.eu-west-1.qanal.io:4434
ℹ To           /data/received/dataset.tar.gz

  5.2 GB / 10.5 GB  [49%]
✓ Download complete: /data/received/dataset.tar.gz
ℹ Received 10.5 GB in 12.3s  (6.83 Gbps)
```

#### Notes

- The transfer must be in `COMPLETED` status before download is available. If it is still `IN_PROGRESS`, the CLI will print a warning and exit — wait for the sender to finish and retry
- If the transfer was routed to an egress relay node (cross-region), the download connects to the egress node (`egressRelayHost:egressDownloadPort`). Otherwise it falls back to `relayHost:(relayQuicPort + 1)`
- Transfers expire after 24 hours. Download before the expiry time shown in `qanal status`

---

### list

Lists recent transfers for your organization, sorted by creation time (newest first).

```
qanal list [options]
```

| Option | Short | Default | Description |
|--------|-------|---------|-------------|
| `--page <n>` | `-p` | `0` | Page number (0-based) |
| `--limit <n>` | `-n` | `20` | Results per page |

#### Example

```bash
qanal list
qanal list --limit 50
qanal list --page 1 --limit 10
```

#### Output

```
ID            FILE                    STATUS        SIZE     PROG  CREATED
────────────────────────────────────────────────────────────────────────────────
019500ab-12…  dataset.tar.gz          COMPLETED   10.5 GB   100%  2 hours ago
019499ff-ab…  report.pdf              IN_PROGRESS  1.2 GB    63%   5 min ago
019499de-cd…  backup-2026-03.tar.gz   CANCELLED   50.0 GB     0%  1 day ago
```

---

### status

Shows detailed status for a single transfer.

```
qanal status <transfer-id>
```

#### Example

```bash
qanal status 019500ab-1234-7abc-def0-123456789abc
```

#### Output

```
Transfer: 019500ab-1234-7abc-def0-123456789abc
  File      : dataset.tar.gz
  Size      : 10.5 GB
  Status    : COMPLETED
  Progress  : 100%  (42 / 42 chunks)
  Throughput: 8.3 Gbps average
  Relay     : relay.us-east-1.qanal.io:4433
  Egress    : relay.eu-west-1.qanal.io:4434
  Created   : 2026-03-05 12:00:00 UTC
  Completed : 2026-03-05 12:00:12 UTC
  Expires   : 2026-03-06 12:00:00 UTC
```

---

### pause

Pauses an in-progress transfer. The DataPlane stops accepting new chunks for this transfer. Resume with `qanal resume`.

```
qanal pause <transfer-id>
```

```bash
qanal pause 019500ab-1234-7abc-def0-123456789abc
# ✓ Transfer paused.
```

Valid from: `IN_PROGRESS` state only.

---

### resume

Resumes a paused transfer.

```
qanal resume <transfer-id>
```

```bash
qanal resume 019500ab-1234-7abc-def0-123456789abc
# ✓ Transfer resumed.
```

Valid from: `PAUSED` state only.

---

### cancel

Cancels a transfer. This is irreversible. The transfer moves to `CANCELLED` status and cannot be resumed.

```
qanal cancel <transfer-id>
```

```bash
qanal cancel 019500ab-1234-7abc-def0-123456789abc
# ✓ Transfer cancelled.
```

Valid from any non-terminal state: `INITIATED`, `WAITING_SENDER`, `IN_PROGRESS`, `PAUSED`.

---

### me

Shows your organization profile and current month's usage.

```
qanal me
```

```
  Name:        Acme Corp
  Plan:        PRO
  Usage:       3.2 TB this month
  Member since: 6 months ago
```

Usage is the total bytes transferred in the current calendar month. Quota limits by plan:

| Plan | Monthly Quota |
|------|---------------|
| FREE | 100 GB |
| PRO | 10 TB |
| ENTERPRISE | Unlimited |

---

## How Sending Works

The send flow in detail:

```
1. Checksum computation
   FileHasher.hexHash(file)
   - Opens the file with FileChannel (streaming, never reads into heap)
   - Feeds 8 MB chunks to lz4-java StreamingXXHash64 (seed = 0)
   - Returns a hex string, e.g. "a1b2c3d4e5f6g7h8"

2. Transfer registration
   POST /api/v1/transfers
   - Sends: fileName, fileSize, fileChecksum, sourceRegion, targetRegion
   - Receives: transfer ID, relay address, chunk plan (list of offsets + sizes)

3. Parallel QUIC upload
   QuicSender opens one QuicChannel to the relay node.
   For each chunk (up to --streams at a time, using a Semaphore):
     - A virtual thread opens a new QUIC stream
     - Writes 68-byte header: transferId + chunkIndex + offsetBytes + sizeBytes + totalFileSize + totalChunks
     - Reads from FileChannel at the chunk's offset (zero-copy, no double-buffering)
     - Streams data in 2 MB pieces
     - Calls shutdownOutput() to signal end-of-chunk

4. Progress tracking
   A background virtual thread subscribes to SSE:
   GET /api/v1/transfers/{id}/progress
   - Receives JSON events every ~500ms: progressPercent, bytesTransferred, currentThroughputBps
   - Renders a terminal progress bar

5. Completion
   After all QUIC streams close, the QuicSender is closed.
   The CLI waits up to 5 seconds for the final SSE COMPLETED event.
```

---

## How Receiving Works

```
1. Fetch transfer info
   GET /api/v1/transfers/{id}
   - Checks status == COMPLETED (exits with warning if not)
   - Gets: fileName, fileSize, downloadHost, downloadPort

2. QUIC download
   QuicDownloader connects to egressRelayHost:egressDownloadPort
   - Opens a bidirectional QUIC stream
   - Sends the 36-byte transferId as the request
   - Receives raw file bytes, writes to FileChannel in 8 MB chunks
   - Progress callback fires every time bytes are written (printed every 500ms)
   - Stream closes when the server has sent all bytes

3. Output
   File is written to the resolved destination path.
   Download stats (bytes received, elapsed time, throughput) are printed.
```

---

## Config File

The config file is stored at `~/.qanal/config.json`. It is created automatically on first `config set-key` or `config set-url` call. The directory `~/.qanal/` is created if it does not exist.

```json
{
  "apiKey": "qnl_a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6",
  "serverUrl": "https://api.yourdomain.com"
}
```

You can edit this file directly. Unknown fields are ignored (`@JsonIgnoreProperties(ignoreUnknown = true)`).

### Per-command overrides

Any command that uses the API key or URL supports inline overrides:

```bash
qanal send file.zip --api-key qnl_other_key --url https://other.server.com
```

These override only for that invocation — the config file is not modified.

---

## Exit Codes

| Code | Meaning |
|------|---------|
| `0` | Success |
| `1` | Error (file not found, API error, transfer failed, network error) |
| `2` | Bad usage (wrong arguments, unknown command) |

---

## Building from Source

```bash
cd CLI

# Build a self-contained fat JAR (includes all dependencies)
./gradlew shadowJar

# Output
ls build/libs/
# qanal-cli-0.1.0-all.jar

# Run directly
java -jar build/libs/qanal-cli-0.1.0-all.jar --help
```

### Run tests

```bash
./gradlew test
```

### Development tips

```bash
# Run without building a JAR
./gradlew run --args="send /path/to/file.zip --to eu-west-1"

# Print version
java -jar build/libs/qanal-cli-*.jar --version
# qanal 0.1.0
```

---

## Troubleshooting

### `No API key configured`

```
No API key configured. Run: qanal config set-key <key>
```

Run `qanal config set-key <your-key>`. Get your API key from your Qanal administrator.

---

### `Transfer is not COMPLETED`

```
Transfer is not COMPLETED (current status: IN_PROGRESS).
Wait until the sender's upload finishes, then retry.
```

The sender is still uploading. Wait and retry `qanal receive` once the transfer is done. Use `qanal status <id>` to check progress.

---

### `Transfer has no egress host`

```
Transfer has no egress host. It may not have been routed to a download node.
```

The transfer was not routed to an egress relay. This happens if the target region has no healthy DataPlane node. Contact your Qanal administrator to register a node in the target region.

---

### Connection timeout / QUIC not connecting

Possible causes:
- UDP port `4433` or `4434` is blocked by a firewall between the client and the server
- The relay node is behind a NAT that blocks incoming UDP
- Corporate VPN is filtering UDP traffic

Fix: ensure UDP ports `4433` and `4434` are open end-to-end between your machine and the relay host. Test with:
```bash
# Check if UDP port 4433 is reachable (requires ncat)
nc -u -v <relay-host> 4433
```

---

### Slow upload speed

- Try increasing parallel streams: `--streams 32` or `--streams 64`
- Ensure you are not throttled by rate limiting (100 requests/min per API key by default)
- Check available disk IOPS on the DataPlane storage node (bottleneck for large files)
- Run `qanal me` to confirm you are not near your monthly quota

---

### `API error 429`

```
API error 429: Rate limit exceeded
```

Your API key has exceeded 100 requests per minute. Wait 60 seconds and retry, or reduce request frequency. If you need a higher rate limit, upgrade to PRO or ENTERPRISE.

---

### `API error 403: Quota exceeded`

Your organization has used its monthly data quota. Options:
- Upgrade to PRO: `curl -X POST https://api.yourdomain.com/api/v1/billing/checkout -H "X-API-Key: qnl_..."`
- Wait for the quota to reset at the start of the next calendar month
- Contact your administrator for an ENTERPRISE quota increase
