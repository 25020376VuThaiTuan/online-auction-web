## API setups

Use the setup that matches how the JavaFX app should reach the auction API.

### Localhost API

Use this when the API and JavaFX client run on the same workstation, with local demo storage and no MySQL dependency.

Start the localhost API in one terminal.

Windows PowerShell:

```powershell
Copy-Item .\scripts\local-api.env.example .\scripts\local-api.env
.\scripts\Start-LocalApi.ps1
```

Linux/macOS:

```sh
cp scripts/local-api.env.example scripts/local-api.env
sh scripts/start-local-api.sh
```

Start the JavaFX client in a second terminal.

Windows PowerShell:

```powershell
.\scripts\Start-LocalClient.ps1
```

Linux/macOS:

```sh
sh scripts/start-local-client.sh
```

The localhost API setup clears `AUCTION_DB_*` for the launched process, sets `AUCTION_API_BASE_URL` to `http://localhost:8081/api`, and uses the local demo accounts: `bidder` / `bid123`, `seller` / `sell123`, and `admin` / `admin123`.

### Internet API

Use this when the API is running on another machine, a LAN/VPN address, a cloud host, or a public URL.
See [REMOTE_MYSQL_SETUP.md](REMOTE_MYSQL_SETUP.md) for server, schema, firewall, API, and JavaFX client steps.
For Azure Database for MySQL plus an Azure-hosted API, see [AZURE_CONNECTION.md](AZURE_CONNECTION.md).

For repeatable internet-mode launches, copy the safe template once and put your real values in the ignored local env file.

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

Then start the standalone API server in one terminal.

Windows PowerShell:

```powershell
.\scripts\Start-RemoteApi.ps1 -EnvFile .\scripts\internet-api.env
```

Linux/macOS:

```sh
sh scripts/start-remote-api.sh scripts/internet-api.env
```

Start the JavaFX client in a second terminal.

Windows PowerShell:

```powershell
.\scripts\Start-RemoteClient.ps1 -EnvFile .\scripts\internet-api.env
```

Linux/macOS:

```sh
sh scripts/start-remote-client.sh scripts/internet-api.env
```

If PowerShell blocks local scripts on your machine, run the same scripts with:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\Start-LocalApi.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\Start-LocalClient.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\Start-RemoteApi.ps1 -EnvFile .\scripts\internet-api.env
powershell -ExecutionPolicy Bypass -File .\scripts\Start-RemoteClient.ps1 -EnvFile .\scripts\internet-api.env
```

`scripts\local-api.env`, `scripts\internet-api.env`, `scripts\azure-api.env`, and `scripts\remote.env` are ignored by Git. Keep real `AUCTION_DB_PASSWORD` values there, not in committed docs or shell history. The same env files are used by both Windows PowerShell and Linux/macOS shell scripts.

### Azure API

Use this when the API should run against Azure Database for MySQL or when the deployed API is hosted on Azure Container Apps or Azure App Service.

Windows PowerShell:

```powershell
Copy-Item .\scripts\azure-api.env.example .\scripts\azure-api.env
notepad .\scripts\azure-api.env
.\scripts\Start-AzureApi.ps1
.\scripts\Start-AzureClient.ps1
```

Linux/macOS:

```sh
cp scripts/azure-api.env.example scripts/azure-api.env
${EDITOR:-vi} scripts/azure-api.env
sh scripts/start-azure-api.sh
sh scripts/start-azure-client.sh
```

The Azure template uses `jdbc:mysql://<server>.mysql.database.azure.com:3306/auctiondb?sslMode=REQUIRED&serverTimezone=UTC`, so the existing MySQL-backed persistence path is reused without changing application code.

## Local run commands

Start the JavaFX desktop app with the default developer run path:

```sh
mvn exec:java
```

The Maven exec plugin points this command at `org.example.Launcher`, the same JavaFX entrypoint used by `mvn javafx:run`.
When `AUCTION_API_BASE_URL` and `AUCTION_DB_*` are not set, the app runs fully offline with local demo storage.
Use `bidder` / `bid123`, `seller` / `sell123`, or `admin` / `admin123` to sign in, or create a new local bidder or seller account.

Start the standalone API server only when you need that process directly:

```sh
mvn exec:java@api-server
```

## Project notes

The codebase includes JavaFX desktop screens, a lightweight HTTP API, local demo storage, MySQL-backed persistence, wallet workflows, auction settlement, automated bidding, and CI/static-analysis configuration.

<img width="1024" height="559" alt="image" src="https://github.com/user-attachments/assets/156727b2-92aa-41b3-b69b-fd30af6b3cc9" />
