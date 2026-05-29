#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
env_file=${1:-"$script_dir/remote.env"}
project_root=$(CDPATH= cd -- "$script_dir/.." && pwd)

# shellcheck source=scripts/load-remote-env.sh
. "$script_dir/load-remote-env.sh"

required_names="AUCTION_DB_URL AUCTION_DB_USER AUCTION_DB_PASSWORD"
optional_names="AUCTION_API_PORT AUCTION_API_WORKER_THREADS AUCTION_API_TOKEN_TTL_SECONDS AUCTION_API_ALLOWED_ORIGIN AUCTION_API_VIRTUAL_THREADS AUCTION_DB_MAX_POOL_SIZE AUCTION_DB_BORROW_TIMEOUT_MILLIS PORT WEBSITES_PORT CONTAINER_APP_PORT"

auction_env_load "$env_file" "$required_names" "$optional_names"

printf 'Loaded API environment from %s\n' "$env_file"
auction_env_summary "$required_names $optional_names"
printf '%s\n' "Starting standalone auction API server..."

cd "$project_root"
exec mvn exec:java@api-server
