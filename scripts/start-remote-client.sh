#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
env_file=${1:-"$script_dir/remote.env"}
project_root=$(CDPATH= cd -- "$script_dir/.." && pwd)

# shellcheck source=scripts/load-remote-env.sh
. "$script_dir/load-remote-env.sh"

required_names="AUCTION_API_BASE_URL"
optional_names="AUCTION_API_CONNECT_TIMEOUT_MILLIS AUCTION_API_REQUEST_TIMEOUT_MILLIS"

auction_env_load "$env_file" "$required_names" "$optional_names"

printf 'Loaded JavaFX client environment from %s\n' "$env_file"
auction_env_summary "$required_names $optional_names"
printf '%s\n' "Starting JavaFX auction client..."

cd "$project_root"
exec mvn exec:java
