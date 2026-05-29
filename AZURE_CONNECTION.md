# Azure connection

This project connects to Azure through the existing remote API path:

- Azure Database for MySQL Flexible Server stores users, auctions, bids, wallets, and sessions.
- The standalone auction API runs in Azure Container Apps or Azure App Service as a custom container.
- The JavaFX desktop client connects to the deployed API through `AUCTION_API_BASE_URL`.

The JavaFX desktop UI is not hosted in Azure. Host the API and database in Azure, then point each desktop client at the Azure API URL.

## Runtime configuration

The API needs these settings:

```text
AUCTION_DB_URL=jdbc:mysql://<MYSQL_SERVER_NAME>.mysql.database.azure.com:3306/auctiondb?sslMode=REQUIRED&serverTimezone=UTC
AUCTION_DB_USER=auction_app
AUCTION_DB_PASSWORD=<runtime-user-password>
AUCTION_API_PORT=8081
```

Azure-hosted containers should route external traffic to port `8081`. The API also accepts common cloud port variables, including `PORT`, `WEBSITES_PORT`, and `CONTAINER_APP_PORT`, but `AUCTION_API_PORT=8081` keeps local scripts and Azure settings explicit.

The JavaFX client needs this setting:

```text
AUCTION_API_BASE_URL=https://<AZURE_API_HOST>/api
```

## Local Azure env file

Copy the committed template and fill in your real Azure values.

Windows PowerShell:

```powershell
Copy-Item .\scripts\azure-api.env.example .\scripts\azure-api.env
notepad .\scripts\azure-api.env
```

Linux/macOS:

```sh
cp scripts/azure-api.env.example scripts/azure-api.env
${EDITOR:-vi} scripts/azure-api.env
```

The copied `scripts\azure-api.env` file is ignored by Git.

## Create Azure MySQL

Set names once for your shell. Resource names must be globally valid for the selected Azure services.

```sh
AZ_RESOURCE_GROUP=rg-online-auction
AZ_LOCATION=eastus
MYSQL_SERVER=replace-with-unique-mysql-server-name
MYSQL_ADMIN_USER=auctionadmin
MYSQL_ADMIN_PASSWORD='replace-with-long-admin-password'

az login
az group create --name "$AZ_RESOURCE_GROUP" --location "$AZ_LOCATION"
```

Create a Flexible Server with public access limited to your current IP for setup:

```sh
CLIENT_IP=$(curl -s https://api.ipify.org)

az mysql flexible-server create \
  --resource-group "$AZ_RESOURCE_GROUP" \
  --name "$MYSQL_SERVER" \
  --location "$AZ_LOCATION" \
  --admin-user "$MYSQL_ADMIN_USER" \
  --admin-password "$MYSQL_ADMIN_PASSWORD" \
  --sku-name Standard_B1ms \
  --tier Burstable \
  --storage-size 32 \
  --version 8.0.21 \
  --public-access "$CLIENT_IP"

az mysql flexible-server db create \
  --resource-group "$AZ_RESOURCE_GROUP" \
  --server-name "$MYSQL_SERVER" \
  --database-name auctiondb
```

For the simple public-access path, also allow Azure-hosted services to reach MySQL:

```sh
az mysql flexible-server firewall-rule create \
  --resource-group "$AZ_RESOURCE_GROUP" \
  --name "$MYSQL_SERVER" \
  --rule-name allow-azure-services \
  --start-ip-address 0.0.0.0 \
  --end-ip-address 0.0.0.0
```

For production, prefer private networking instead of the broad Azure-services firewall rule.

## Load schema and create a runtime user

Run this from the repository root after the server is created:

```sh
MYSQL_HOST="$MYSQL_SERVER.mysql.database.azure.com"

mysql -h "$MYSQL_HOST" -P 3306 -u "$MYSQL_ADMIN_USER" -p < schema.sql
```

Then create the least-privilege runtime user used by the Java API:

```sql
CREATE USER IF NOT EXISTS 'auction_app'@'%' IDENTIFIED BY '<runtime-user-password>';

GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
ON auctiondb.*
TO 'auction_app'@'%';

FLUSH PRIVILEGES;
```

Test the runtime connection:

```sh
mysql -h "$MYSQL_HOST" -P 3306 -u auction_app -p auctiondb -e "SHOW TABLES;"
```

## Run the API locally against Azure MySQL

After `scripts\azure-api.env` has the Azure MySQL URL, user, and password:

Windows PowerShell:

```powershell
.\scripts\Start-AzureApi.ps1
```

Linux/macOS:

```sh
sh scripts/start-azure-api.sh
```

The API startup should print:

```text
Database preflight succeeded for remote MySQL.
Auction API server is listening on http://0.0.0.0:8081/api
```

## Deploy the API to Azure Container Apps

The included `Dockerfile` builds only the standalone API server and exposes port `8081`.

```sh
CONTAINER_APP=online-auction-api
JDBC_URL="jdbc:mysql://$MYSQL_SERVER.mysql.database.azure.com:3306/auctiondb?sslMode=REQUIRED&serverTimezone=UTC"

az containerapp up \
  --name "$CONTAINER_APP" \
  --resource-group "$AZ_RESOURCE_GROUP" \
  --location "$AZ_LOCATION" \
  --source . \
  --ingress external \
  --target-port 8081 \
  --env-vars \
    AUCTION_DB_URL="$JDBC_URL" \
    AUCTION_DB_USER=auction_app \
    AUCTION_DB_PASSWORD='replace-with-runtime-user-password' \
    AUCTION_API_PORT=8081

API_FQDN=$(az containerapp show \
  --name "$CONTAINER_APP" \
  --resource-group "$AZ_RESOURCE_GROUP" \
  --query properties.configuration.ingress.fqdn \
  --output tsv)

curl "https://$API_FQDN/api/health"
```

Set `AUCTION_API_BASE_URL=https://$API_FQDN/api` in `scripts\azure-api.env` for desktop clients.
For production, store `AUCTION_DB_PASSWORD` as a Container Apps secret and reference it from the environment variable instead of passing the password directly in shell history.

## Deploy the API with GitHub Actions

The separate `.github/workflows/azure-api.yml` workflow builds the existing `Dockerfile`, publishes `online-auction-api` to Azure Container Registry, deploys the pushed image to an existing Azure Container App, and then checks `https://<container-app-fqdn>/api/health`.

Run it manually from GitHub Actions as **Azure API Container Deploy** after the Azure resources below exist:

- Azure Container Registry named by `AZURE_CONTAINER_REGISTRY_NAME`.
- Azure Container App named by `AZURE_CONTAINER_APP_NAME` in `AZURE_RESOURCE_GROUP`.
- The Container App must be allowed to pull from the registry, usually with a managed identity that has `AcrPull` on the registry.
- The Container App ingress should be public for desktop clients; the workflow enforces external ingress on target port `8081`.

Configure these repository or environment secrets:

| Secret | Purpose |
| --- | --- |
| `AZURE_CLIENT_ID` | Microsoft Entra application or managed identity client ID used by GitHub OIDC. |
| `AZURE_TENANT_ID` | Azure tenant ID. |
| `AZURE_SUBSCRIPTION_ID` | Azure subscription ID. |
| `AUCTION_DB_PASSWORD` | Runtime password for the Azure MySQL user in `AUCTION_DB_USER`. |

Configure these repository or environment variables:

| Variable | Example | Purpose |
| --- | --- | --- |
| `AZURE_CONTAINER_REGISTRY_NAME` | `myauctionacr` | ACR name without `.azurecr.io`. |
| `AZURE_RESOURCE_GROUP` | `rg-online-auction` | Resource group containing the Container App. |
| `AZURE_CONTAINER_APP_NAME` | `online-auction-api` | Existing Azure Container App to update. |
| `AUCTION_DB_URL` | `jdbc:mysql://<server>.mysql.database.azure.com:3306/auctiondb?sslMode=REQUIRED&serverTimezone=UTC` | JDBC URL for Azure Database for MySQL. |
| `AUCTION_DB_USER` | `auction_app` | Runtime MySQL user. |

The Azure identity used by `AZURE_CLIENT_ID` needs enough permission to push to ACR and update the Container App. A typical minimum is `AcrPush` on the registry plus `Contributor` on the Container App or its resource group. GitHub OIDC also requires a federated credential on the Azure identity for this repository, branch, and environment.

## Deploy the API to Azure App Service custom container

Use this path if you prefer App Service instead of Container Apps.

```sh
ACR_NAME=replacewithuniqueacrname
APP_SERVICE_PLAN=online-auction-plan
WEBAPP_NAME=replace-with-unique-web-app-name
IMAGE_NAME=online-auction-api:latest

az acr create \
  --resource-group "$AZ_RESOURCE_GROUP" \
  --name "$ACR_NAME" \
  --sku Basic

az acr update \
  --resource-group "$AZ_RESOURCE_GROUP" \
  --name "$ACR_NAME" \
  --admin-enabled true

az acr build \
  --registry "$ACR_NAME" \
  --image "$IMAGE_NAME" \
  .

ACR_LOGIN_SERVER=$(az acr show \
  --resource-group "$AZ_RESOURCE_GROUP" \
  --name "$ACR_NAME" \
  --query loginServer \
  --output tsv)

ACR_USERNAME=$(az acr credential show \
  --resource-group "$AZ_RESOURCE_GROUP" \
  --name "$ACR_NAME" \
  --query username \
  --output tsv)

ACR_PASSWORD=$(az acr credential show \
  --resource-group "$AZ_RESOURCE_GROUP" \
  --name "$ACR_NAME" \
  --query "passwords[0].value" \
  --output tsv)

az appservice plan create \
  --resource-group "$AZ_RESOURCE_GROUP" \
  --name "$APP_SERVICE_PLAN" \
  --is-linux \
  --sku B1

az webapp create \
  --resource-group "$AZ_RESOURCE_GROUP" \
  --plan "$APP_SERVICE_PLAN" \
  --name "$WEBAPP_NAME" \
  --container-image-name "$ACR_LOGIN_SERVER/$IMAGE_NAME"

az webapp config container set \
  --resource-group "$AZ_RESOURCE_GROUP" \
  --name "$WEBAPP_NAME" \
  --docker-custom-image-name "$ACR_LOGIN_SERVER/$IMAGE_NAME" \
  --docker-registry-server-url "https://$ACR_LOGIN_SERVER" \
  --docker-registry-server-user "$ACR_USERNAME" \
  --docker-registry-server-password "$ACR_PASSWORD"

az webapp config appsettings set \
  --resource-group "$AZ_RESOURCE_GROUP" \
  --name "$WEBAPP_NAME" \
  --settings \
    WEBSITES_PORT=8081 \
    AUCTION_API_PORT=8081 \
    AUCTION_DB_URL="$JDBC_URL" \
    AUCTION_DB_USER=auction_app \
    AUCTION_DB_PASSWORD='replace-with-runtime-user-password'

curl "https://$WEBAPP_NAME.azurewebsites.net/api/health"
```

Set `AUCTION_API_BASE_URL=https://$WEBAPP_NAME.azurewebsites.net/api` in `scripts\azure-api.env` for desktop clients.
The commands above use ACR admin credentials for a short setup path. For production, switch App Service to managed identity with `AcrPull` on the registry.

## Start the JavaFX client against Azure

Windows PowerShell:

```powershell
.\scripts\Start-AzureClient.ps1
```

Linux/macOS:

```sh
sh scripts/start-azure-client.sh
```

If sign-in works through the Azure API URL, the project is fully connected to Azure: desktop client to Azure API, Azure API to Azure MySQL.

## Troubleshooting

- `Database preflight failed`: check `AUCTION_DB_URL`, `AUCTION_DB_USER`, `AUCTION_DB_PASSWORD`, MySQL firewall rules, and whether `schema.sql` was loaded.
- `Communications link failure`: the API host cannot reach MySQL port `3306`; fix Azure MySQL networking or use private VNet access.
- `Access denied`: verify the runtime user and password, then test with the `mysql` command above.
- Azure container starts but health check fails: confirm port `8081`, `AUCTION_API_PORT`, and App Service `WEBSITES_PORT`.
- Desktop app still uses local data: set `AUCTION_API_BASE_URL`; the desktop client does not read `AUCTION_DB_*` directly.
