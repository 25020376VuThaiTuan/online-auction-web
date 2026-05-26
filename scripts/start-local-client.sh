#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
env_file=${1:-"$script_dir/local-api.env"}
project_root=$(CDPATH= cd -- "$script_dir/.." && pwd)

# shellcheck source=scripts/load-remote-env.sh
. "$script_dir/load-remote-env.sh"

optional_names="AUCTION_API_BASE_URL AUCTION_API_PORT AUCTION_API_CONNECT_TIMEOUT_MILLIS AUCTION_API_REQUEST_TIMEOUT_MILLIS"

if [ -f "$env_file" ]; then
    auction_env_load "$env_file" "" "$optional_names"
    printf 'Loaded localhost client environment from %s\n' "$env_file"
elif [ "$#" -gt 0 ]; then
    auction_env_die "Env file '$env_file' was not found."
else
    printf 'No localhost client env file found at %s; using http://localhost:8081/api.\n' "$env_file"
fi

AUCTION_API_PORT=${AUCTION_API_PORT:-8081}
AUCTION_API_BASE_URL=${AUCTION_API_BASE_URL:-"http://localhost:$AUCTION_API_PORT/api"}
export AUCTION_API_PORT AUCTION_API_BASE_URL
unset AUCTION_DB_URL AUCTION_DB_USER AUCTION_DB_PASSWORD

auction_env_summary "$optional_names"
printf '%s\n' "Starting JavaFX auction client against the localhost API..."

cd "$project_root"
exec mvn exec:java
