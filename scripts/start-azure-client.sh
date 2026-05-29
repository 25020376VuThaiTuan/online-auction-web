#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
env_file=${1:-"$script_dir/azure-api.env"}

exec sh "$script_dir/start-remote-client.sh" "$env_file"
