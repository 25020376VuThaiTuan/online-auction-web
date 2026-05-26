#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
env_file=${1:-"$script_dir/local-api.env"}
project_root=$(CDPATH= cd -- "$script_dir/.." && pwd)

# shellcheck source=scripts/load-remote-env.sh
. "$script_dir/load-remote-env.sh"

optional_names="AUCTION_API_PORT AUCTION_API_WORKER_THREADS AUCTION_API_TOKEN_TTL_SECONDS AUCTION_DEMO_ACCOUNTS_ENABLED"

if [ -f "$env_file" ]; then
    auction_env_load "$env_file" "" "$optional_names"
    printf 'Loaded localhost API environment from %s\n' "$env_file"
elif [ "$#" -gt 0 ]; then
    auction_env_die "Env file '$env_file' was not found."
else
    printf 'No localhost API env file found at %s; using local demo defaults.\n' "$env_file"
fi

AUCTION_API_PORT=${AUCTION_API_PORT:-8081}
AUCTION_DEMO_ACCOUNTS_ENABLED=${AUCTION_DEMO_ACCOUNTS_ENABLED:-true}
export AUCTION_API_PORT AUCTION_DEMO_ACCOUNTS_ENABLED
unset AUCTION_DB_URL AUCTION_DB_USER AUCTION_DB_PASSWORD

auction_env_summary "$optional_names"
printf '%s\n' "Starting localhost auction API server with local demo storage..."

cd "$project_root"
exec mvn exec:java@api-server
