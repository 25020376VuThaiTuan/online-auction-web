# Online Auction Web

## 1. Problem Statement and System Scope

This project is an online auction platform with:

- A JavaFX desktop client
- An HTTP API server for authentication, user management, wallet operations, items, and auctions
- Local demo storage for offline use, or MySQL-backed remote mode

The system supports three main roles:

- `Bidder`: joins auctions, tops up or withdraws funds, places bids, and uses auto-bid
- `Seller`: submits items and creates auctions
- `Admin`: approves items, manages users, and monitors settlement results

Implemented scope includes:

- Registration, login, and logout
- Profile and wallet management
- Item submission, approval, and auction listing
- Bidding, auto-bid, and entry deposit handling
- Auction settlement and notifications
- Local demo mode and remote MySQL mode

## 2. Technology, Runtime, and Installation Requirements

### Technology Stack

- Java 21
- Maven
- JavaFX 21
- `com.sun.net.httpserver.HttpServer`
- JDBC MySQL Connector
- H2 for tests
- JUnit 5
- BCrypt for password and recovery-code hashing

### Runtime Environment

- Windows, Linux, and macOS
- The Maven commands below work on all three operating systems
- Remote/MySQL mode requires MySQL 8+ or a compatible hosted MySQL service

### Installation Requirements

Check your local setup:

```sh
java -version
mvn -version
```

Minimum requirements:

- JDK 21
- Apache Maven 3.9+
- MySQL only if you use remote mode

## 3. Project Structure

- `src/main/java/org/example/controller`: JavaFX controllers
- `src/main/java/org/example/service`: business logic
- `src/main/java/org/example/model`: domain entities and value objects
- `src/main/java/org/example/dao`: JDBC and data access
- `src/main/java/org/example/server`: HTTP API server, routing, sessions, and realtime broker
- `src/main/java/org/example/client`: API client
- `src/main/java/org/example/util`: shared utilities
- `src/main/resources/view`: FXML and CSS files
- `src/main/resources/db/migration`: schema migrations
- `src/test/java`: unit and integration tests
- `scripts`: helper scripts for local, remote, and Azure runs
- `schema.sql`: MySQL schema
- `REMOTE_MYSQL_SETUP.md`: remote MySQL setup guide
- `AZURE_CONNECTION.md`: Azure MySQL setup guide

## 4. Command-Line Run Commands

### Standard Run, Works on Windows/Linux/macOS

Open two terminals.

Terminal 1, start the API server:

```sh
mvn exec:java@api-server
```

Terminal 2, start the JavaFX client:

```sh
mvn exec:java
```

### Local Demo Scripts

Windows PowerShell:

```powershell
.\scripts\Start-LocalApi.ps1
.\scripts\Start-LocalClient.ps1
```

Linux/macOS:

```sh
sh scripts/start-local-api.sh
sh scripts/start-local-client.sh
```

### Remote/MySQL Mode

Set these environment variables before starting the server:

- `AUCTION_DB_URL`
- `AUCTION_DB_USER`
- `AUCTION_DB_PASSWORD`

Start the server:

```sh
mvn exec:java@api-server
```

Point the client to the running API using a cross-platform JVM property:

```sh
mvn -Dauction.api.baseUrl=http://localhost:8081/api exec:java
```

Remote scripts are also available:

Windows PowerShell:

```powershell
.\scripts\Start-RemoteApi.ps1 -EnvFile .\scripts\internet-api.env
.\scripts\Start-RemoteClient.ps1 -EnvFile .\scripts\internet-api.env
```

Linux/macOS:

```sh
sh scripts/start-remote-api.sh scripts/internet-api.env
sh scripts/start-remote-client.sh scripts/internet-api.env
```

## 5. Server/Client Start Order

Recommended order:

1. Start the server first
2. Wait until it listens on port `8081` or the port you configured
3. Start the client second
4. Log in with an appropriate account

### Local Demo Accounts

- `bidder` / `bid123`
- `seller` / `sell123`
- `admin` / `admin123`

## 6. Completed Features

- Bidder or seller registration
- Login, logout, and current-user lookup
- Password recovery and reset
- Profile management
- Wallet management: PIN, wallet auth, top up, withdraw, and linked accounts
- Wallet PIN recovery and reset
- Item submission and auction creation
- Admin item approval
- Auction list, auction details, and pending item views
- Bidding and auto-bid support
- Entry deposit handling
- Auction settlement recording
- User notifications
- Realtime auction event streaming
- Local demo storage and remote MySQL support

## 7. Notes

- Apply `schema.sql` before starting the server in remote/MySQL mode.
- If port `8081` is already in use, change it with `AUCTION_API_PORT` or `--port`.
- `mvn exec:java` is the default client launch command for this repository.
