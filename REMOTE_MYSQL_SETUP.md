# Remote MySQL setup

This project can run against a remote MySQL database when these environment variables are set:

- `AUCTION_DB_URL`
- `AUCTION_DB_USER`
- `AUCTION_DB_PASSWORD`

If they are not set, the API uses local demo data. The desktop client talks to the API through `AUCTION_API_BASE_URL`.

For Azure Database for MySQL Flexible Server and Azure-hosted API steps, use [AZURE_CONNECTION.md](AZURE_CONNECTION.md).

For repeatable internet API launches on Windows, Linux, and macOS, use the committed template plus the ignored local env file.

Windows PowerShell:

```powershell
Copy-Item .\scripts\internet-api.env.example .\scripts\internet-api.env
notepad .\scripts\internet-api.env
```

Linux/macOS:

```sh
cp scripts/internet-api.env.example scripts/internet-api.env
${EDITOR:-vi} scripts/internet-api.env
```

Then start the two processes in separate terminals.

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

The real `scripts\internet-api.env` file is ignored by Git so database passwords stay out of commits.

## 1. Install MySQL on the remote server

Ubuntu example:

```sh
sudo apt update
sudo apt install mysql-server
sudo systemctl enable --now mysql
sudo mysql_secure_installation
```

Confirm MySQL is running:

```sh
sudo systemctl status mysql
```

## 2. Create the database and app user

Log in as a MySQL admin on the remote server:

```sh
sudo mysql
```

Create the database and a restricted runtime user:

```sql
CREATE DATABASE IF NOT EXISTS auctiondb
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'auction_app'@'%' IDENTIFIED BY 'replace_with_a_long_password';

GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
ON auctiondb.*
TO 'auction_app'@'%';

FLUSH PRIVILEGES;
```

For tighter access, replace `%` with the public or private IP address of the machine that will run the auction API.

## 3. Allow remote TCP connections

Edit the MySQL server config:

```sh
sudo nano /etc/mysql/mysql.conf.d/mysqld.cnf
```

Set `bind-address` to the server private IP, or to all interfaces if the firewall is locked down:

```ini
bind-address = 0.0.0.0
```

Restart MySQL:

```sh
sudo systemctl restart mysql
```

Open the firewall only to the API host:

```sh
sudo ufw allow from <API_SERVER_IP> to any port 3306 proto tcp
```

If the server is on AWS, Azure, GCP, DigitalOcean, or another cloud provider, also add an inbound security-group/firewall rule for TCP `3306` from the API host only.

## 4. Load the project schema

From this repository, copy `schema.sql` to the remote server, then run:

```sh
mysql -u root -p < schema.sql
```

For a fresh database, `schema.sql` already contains the current schema. Use it as the normal setup path.

Only if you are upgrading an older database that was created from an earlier version of this repository, apply the bundled migrations in order. These migration files live in `src/main/resources/db/migration/`:

```sh
mysql -u root -p auctiondb < src/main/resources/db/migration/V2__wallet_accounts_and_authorization.sql
mysql -u root -p auctiondb < src/main/resources/db/migration/V3__wallet_linked_account_balance.sql
mysql -u root -p auctiondb < src/main/resources/db/migration/V4__wallet_holds.sql
mysql -u root -p auctiondb < src/main/resources/db/migration/V5__auto_bid_increment.sql
mysql -u root -p auctiondb < src/main/resources/db/migration/V6__hashed_wallet_recovery_codes.sql
```

The API startup preflight also attempts these supported schema repairs when the configured `AUCTION_DB_USER` has the `CREATE`, `ALTER`, and `INDEX` privileges granted above. Applying the SQL files manually first is still the clearest path for production databases because it makes the upgrade explicit and reviewable.

## 5. Test the database connection

From the machine that will run the API:

```sh
mysql -h <MYSQL_HOST> -P 3306 -u auction_app -p auctiondb -e "SHOW TABLES;"
```

If this fails, fix networking before starting the Java app. The common causes are `bind-address`, OS firewall, cloud firewall/security group, or using a MySQL user host that does not match the client machine.

## 6. Create the internet API env file

The safe committed template is `scripts\internet-api.env.example`. Copy it once.

Windows PowerShell:

```powershell
Copy-Item .\scripts\internet-api.env.example .\scripts\internet-api.env
```

Linux/macOS:

```sh
cp scripts/internet-api.env.example scripts/internet-api.env
```

Edit `scripts\internet-api.env` and replace the placeholders:

```text
AUCTION_DB_URL=jdbc:mysql://<MYSQL_HOST>:3306/auctiondb?sslMode=REQUIRED&serverTimezone=UTC
AUCTION_DB_USER=auction_app
AUCTION_DB_PASSWORD=replace_with_a_long_password
AUCTION_API_PORT=8081
AUCTION_API_BASE_URL=https://auction.example.com/api
```

Use this development-only URL only if the MySQL server does not have TLS configured yet:

```text
AUCTION_DB_URL=jdbc:mysql://<MYSQL_HOST>:3306/auctiondb?sslMode=DISABLED&allowPublicKeyRetrieval=true&serverTimezone=UTC
```

The env file uses plain `KEY=value` lines. Quoted values are also accepted. You do not need shell `export` commands, and PowerShell `$env:` assignments do not belong in this file. The included scripts parse the file as data so JDBC URLs with `&` work consistently on every OS.

## 7. Run the API against remote MySQL

Open a terminal from the repository root and run the command for your OS.

Windows PowerShell:

```powershell
.\scripts\Start-RemoteApi.ps1 -EnvFile .\scripts\internet-api.env
```

If PowerShell execution policy blocks local scripts, use:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\Start-RemoteApi.ps1 -EnvFile .\scripts\internet-api.env
```

Linux/macOS:

```sh
sh scripts/start-remote-api.sh scripts/internet-api.env
```

If you prefer executable shell scripts:

```sh
chmod +x scripts/start-remote-api.sh scripts/start-remote-client.sh
./scripts/start-remote-api.sh scripts/internet-api.env
```

The API script loads `AUCTION_DB_URL`, `AUCTION_DB_USER`, `AUCTION_DB_PASSWORD`, and optional API server values such as `AUCTION_API_PORT` from `scripts\internet-api.env`, masks secret values in its summary, then runs:

```powershell
mvn exec:java@api-server
```

Use a different env file when needed.

Windows PowerShell:

```powershell
.\scripts\Start-RemoteApi.ps1 -EnvFile .\scripts\remote-staging.env
```

Linux/macOS:

```sh
sh scripts/start-remote-api.sh scripts/remote-staging.env
```

The script stops before Maven starts if any required `AUCTION_DB_*` value is missing, so remote mode does not silently fall back to local demo data.

One-off manual PowerShell commands still work:

```powershell
$env:AUCTION_DB_URL="jdbc:mysql://<MYSQL_HOST>:3306/auctiondb?sslMode=REQUIRED&serverTimezone=UTC"
$env:AUCTION_DB_USER="auction_app"
$env:AUCTION_DB_PASSWORD="replace_with_a_long_password"
$env:AUCTION_API_PORT="8081"
mvn exec:java@api-server
```

Linux/macOS shell:

```sh
export AUCTION_DB_URL="jdbc:mysql://<MYSQL_HOST>:3306/auctiondb?sslMode=REQUIRED&serverTimezone=UTC"
export AUCTION_DB_USER="auction_app"
export AUCTION_DB_PASSWORD="replace_with_a_long_password"
export AUCTION_API_PORT="8081"
mvn exec:java@api-server
```

The API should print:

```text
Database preflight succeeded for remote MySQL.
Auction API server is listening on http://0.0.0.0:8081/api
```

Check health:

```sh
curl http://localhost:8081/api/health
```

## 8. Connect the JavaFX desktop client

In a second terminal, after the API server is listening, run the command for your OS.

Windows PowerShell:

```powershell
.\scripts\Start-RemoteClient.ps1 -EnvFile .\scripts\internet-api.env
```

If PowerShell execution policy blocks local scripts, use:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\Start-RemoteClient.ps1 -EnvFile .\scripts\internet-api.env
```

Linux/macOS:

```sh
sh scripts/start-remote-client.sh scripts/internet-api.env
```

The client script loads `AUCTION_API_BASE_URL` and optional timeout values from `scripts\internet-api.env`, then runs:

```powershell
mvn exec:java
```

Use a different env file when needed.

Windows PowerShell:

```powershell
.\scripts\Start-RemoteClient.ps1 -EnvFile .\scripts\remote-staging.env
```

Linux/macOS:

```sh
sh scripts/start-remote-client.sh scripts/remote-staging.env
```

If the API is running on the same machine as the desktop app:

```text
AUCTION_API_BASE_URL=http://localhost:8081/api
```

If the API is running on a remote host:

```text
AUCTION_API_BASE_URL=http://<API_HOST>:8081/api
```

If you use a reverse proxy such as Nginx in front of the API, HTTP is still accepted:

```text
AUCTION_API_BASE_URL=http://auction.example.com/api
```

One-off manual PowerShell commands still work:

```powershell
$env:AUCTION_API_BASE_URL="http://localhost:8081/api"
mvn exec:java
```

## 9. Optional persistent Windows environment variables

This is not needed when using `scripts\internet-api.env`. Use `setx` only if you prefer user-level Windows variables instead of the env-file script flow.

`setx` applies to new terminals only:

```powershell
setx AUCTION_DB_URL "jdbc:mysql://<MYSQL_HOST>:3306/auctiondb?sslMode=REQUIRED&serverTimezone=UTC"
setx AUCTION_DB_USER "auction_app"
setx AUCTION_DB_PASSWORD "replace_with_a_long_password"
setx AUCTION_API_PORT "8081"
setx AUCTION_API_BASE_URL "https://auction.example.com/api"
```

Keep real passwords out of Git, screenshots, and shared shell history.

## Troubleshooting

- `Access denied`: check `AUCTION_DB_USER`, `AUCTION_DB_PASSWORD`, and the MySQL user host, for example `'auction_app'@'%'` or `'auction_app'@'<API_SERVER_IP>'`.
- `Unknown database`: create `auctiondb` or update the database name in `AUCTION_DB_URL`.
- `Communications link failure`: check host, port, `bind-address`, firewall, and cloud security group rules.
- `Public Key Retrieval is not allowed`: use TLS with `sslMode=REQUIRED`, or for trusted development only add `allowPublicKeyRetrieval=true`.
- `Missing required table` or `Missing required column`: apply `schema.sql` to a fresh database, or apply the bundled files in `src/main/resources/db/migration/` in order for an older database created from this repository.
- Desktop app still uses local data: set `AUCTION_API_BASE_URL`; the DB variables are used by the API server, not directly by the desktop UI.
