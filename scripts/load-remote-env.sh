#!/usr/bin/env sh

auction_env_loaded_names=

auction_env_die() {
    printf '%s\n' "$*" >&2
    exit 1
}

auction_env_contains_name() {
    case " $1 " in
        *" $2 "*) return 0 ;;
        *) return 1 ;;
    esac
}

auction_env_trim() {
    value=$1

    while :; do
        case $value in
            ' '* | '	'*) value=${value#?} ;;
            *) break ;;
        esac
    done

    while :; do
        case $value in
            *' ' | *'	') value=${value%?} ;;
            *) break ;;
        esac
    done

    printf '%s' "$value"
}

auction_env_load() {
    env_file=$1
    required_names=$2
    optional_names=$3
    allowed_names=" $required_names $optional_names "
    line_number=0

    if [ ! -f "$env_file" ]; then
        auction_env_die "Env file '$env_file' was not found. Copy one of the scripts/*.env.example templates and fill in your local values."
    fi

    while IFS= read -r raw_line || [ -n "$raw_line" ]; do
        line_number=$((line_number + 1))
        line=$(auction_env_trim "$raw_line")

        case $line in
            '' | \#*) continue ;;
            export\ *) line=$(auction_env_trim "${line#export }") ;;
        esac

        case $line in
            *=*) ;;
            *) auction_env_die "Invalid env assignment in '$env_file' on line $line_number. Use KEY=value." ;;
        esac

        name=$(auction_env_trim "${line%%=*}")
        value=$(auction_env_trim "${line#*=}")

        case $name in
            '' | *[!ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_]* | [0123456789]*)
                auction_env_die "Invalid env variable name '$name' in '$env_file' on line $line_number."
                ;;
        esac

        if ! auction_env_contains_name "$allowed_names" "$name"; then
            continue
        fi

        if [ ${#value} -ge 2 ]; then
            first_char=${value%"${value#?}"}
            last_char=${value#"${value%?}"}
            if { [ "$first_char" = '"' ] && [ "$last_char" = '"' ]; } ||
                { [ "$first_char" = "'" ] && [ "$last_char" = "'" ]; }; then
                value=${value#?}
                value=${value%?}
            fi
        fi

        export "$name=$value"
        auction_env_loaded_names="$auction_env_loaded_names $name"
    done < "$env_file"

    for required_name in $required_names; do
        eval "required_value=\${$required_name-}"
        if [ -z "$required_value" ]; then
            auction_env_die "Required environment variable '$required_name' is missing. Add it to '$env_file'."
        fi
    done
}

auction_env_summary() {
    names=$1

    printf '%s\n' "Effective environment:"
    for name in $names; do
        eval "value=\${$name-}"
        if [ -z "$value" ]; then
            continue
        fi

        case $name in
            *PASSWORD* | *SECRET* | *TOKEN*) value="<set>" ;;
        esac

        printf '  %s=%s\n' "$name" "$value"
    done
}
