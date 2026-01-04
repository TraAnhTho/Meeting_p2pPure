# MeetHub (P2P Meeting App)

JavaFX desktop application for creating/joining meetings with **P2P mesh signaling** (TCP) and **UDP media streaming** (audio/video) + basic file sharing.

## Tech stack

- Java **17**
- JavaFX **17.0.14**
- Maven
- SQLite (local DB: `meeting_app.db`)
- Webcam capture: `com.github.sarxos:webcam-capture`

## Prerequisites

- JDK **17** installed and configured (`JAVA_HOME` points to JDK 17)
- Maven (or use the included Maven Wrapper: `mvnw` / `mvnw.cmd`)
- Windows: allow the app through Firewall for local LAN testing (TCP/UDP)

## Project structure (high-level)

- `src/main/java/com/example/dacs4/App.java`: JavaFX entry point
- `src/main/java/com/example/dacs4/controllers/*`: UI controllers
- `src/main/java/com/example/dacs4/network/*`: P2P + media + file transfer logic
- `src/main/resources/fxml/*`: JavaFX FXML UI
- `src/main/resources/css/*`: UI styles
- `meeting_app.db`: local SQLite database (auto-created)
- `downloads/`: received/shared files are stored here (auto-created)

## Run (recommended)

### Option A: Run with Maven Wrapper

From the project root:

```bash
# Windows
mvnw.cmd clean javafx:run

# macOS / Linux
./mvnw clean javafx:run
```

### Option B: Run from IntelliJ IDEA

1. Open the project as a Maven project.
2. Ensure Project SDK is **JDK 17**.
3. Run the main class:
   - `com.example.dacs4.App`

> If you see module warnings, verify `module-info.java` and make sure you are running with JDK 17.

## How to test P2P meeting (2 instances)

You need **2 app instances** (same machine or two machines on the same LAN).

1. Instance #1 (Host)
   - Login
   - Create / start a meeting (you will see a meeting id like `1RRAWZ`)

2. Instance #2 (Participant)
   - Login
   - Join using the meeting id

### Notes

- Signaling uses TCP with per-user listening port:
  - base `5000` + `abs(userId.hashCode() % 1000)`
- Audio uses UDP:
  - base `6000` + `abs(userId.hashCode() % 1000)`
- Video uses UDP:
  - base `7000` + `abs(userId.hashCode() % 1000)`

## File sharing

- Files are transferred in chunks over P2P signaling.
- Received files are written to:
  - `downloads/<fileName>`
- In the File Sharing panel:
  - The download button opens **Save As** (copies from `downloads/`)
  - If the file is an image (`png/jpg/jpeg/gif`) and exists locally, a thumbnail is shown and can be clicked to preview.

## Troubleshooting

- **Participant cannot join / Meeting not found**
  - Ensure both machines are on the same LAN.
  - Check Windows Firewall rules.
  - Allow UDP discovery port `50555`.

- **Camera not working**
  - Ensure a webcam is available and not used by another app.

- **Audio/video not received**
  - UDP ports must not be blocked by firewall.

## Build

```bash
mvnw.cmd clean package
```

## Author / Contributors

- **Author**: <Trà Anh Thơ> (<https://github.com/TraAnhTho/Meeting_p2pPure>)
- **Contributors**: 

## License

For study/demo purposes.
