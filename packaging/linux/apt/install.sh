#!/bin/sh
# Adds the FileBot Pro APT repository and installs FileBot Pro on Ubuntu, Debian and derivatives:
#
#   wget -qO- https://apt.davamu.dev/install.sh | sudo sh     (or curl -fsSL ... | sudo sh)
#
# Writes /etc/apt/sources.list.d/filebot-pro.sources (the repository with its public key), then runs apt-get update and
# apt-get install filebot-pro. Running it again repairs or updates the setup.
#
# Template: update-repository.sh replaces the @{...} tokens and publishes it as https://apt.davamu.dev/install.sh.

set -eu

# everything runs from main, so a download cut short runs nothing and apt cannot read the rest of the script from stdin
main() {
    sources=/etc/apt/sources.list.d/filebot-pro.sources
    keyring=/etc/apt/keyrings/filebot-pro.asc

    [ "$(id -u)" -eq 0 ] || fail "Run it as root: wget -qO- https://apt.davamu.dev/install.sh | sudo sh"
    command -v apt-get > /dev/null 2>&1 || fail "apt-get not found. This script is for Ubuntu, Debian and derivatives."

    # the key inside the source (Signed-By with the key block) needs apt 2.3.10 or later
    apt_version=$(dpkg-query --showformat='${Version}' --show apt)
    dpkg --compare-versions "$apt_version" ge 2.3.10 \
        || fail "apt $apt_version is too old. FileBot Pro needs Ubuntu 22.04, Debian 12 or later."

    arch=$(dpkg --print-architecture)
    available="@{architectures}"
    case " $available " in
        *" $arch "*) ;;
        *) fail "There is no FileBot Pro package for $arch (available: $available)." ;;
    esac

    # a second entry for the repository with another Signed-By makes apt update fail
    others=$(grep -lsE '^[^#]*apt\.davamu\.dev' /etc/apt/sources.list /etc/apt/sources.list.d/* | grep -vxF "$sources" || true)
    [ -z "$others" ] || fail "The repository is already set up in $(echo "$others" | paste -sd ' ' -). Remove it there and run this script again."

    # an earlier setup is replaced (it would also make the apt-get update below fail if it is broken); its separate key
    # is now part of the source
    rm -f "$sources" "$keyring"

    # the repository is only reachable over HTTPS
    if [ ! -s /etc/ssl/certs/ca-certificates.crt ]; then
        apt-get update < /dev/null
        apt-get install -y ca-certificates < /dev/null
    fi

    echo "Adding the FileBot Pro repository to $sources"
    cat > "$sources.tmp" <<'EOF'
@{sources}
EOF
    chmod 0644 "$sources.tmp"
    mv "$sources.tmp" "$sources"

    apt-get update < /dev/null
    apt-get install -y filebot-pro < /dev/null

    echo
    echo "FileBot Pro is installed. Start it from the application menu or type filebot-pro in a terminal."
    echo "New versions arrive with your other updates (sudo apt update && sudo apt upgrade)."
}

fail() {
    echo "Error: $*" >&2
    exit 1
}

main
