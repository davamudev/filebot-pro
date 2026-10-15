#!/bin/sh
# Generates the signed indexes of the APT repository for the given .deb packages (used by the "APT repository"
# workflow, .github/workflows/apt.yml):
#
#   update-repository.sh <output folder> <package.deb>...
#
# The output folder becomes the static assets of the Cloudflare Worker in worker/, which redirects the packages to the
# GitHub release assets, so the packages themselves are not part of it:
#   dists/stable/Release, InRelease, Release.gpg     signed with the first secret key of the gpg keyring
#   dists/stable/main/binary-<arch>/Packages(.gz)    Filename: pool/main/<package>_<version>_<arch>.deb
#   filebot-pro.asc, filebot-pro.sources             public key and deb822 source for /etc/apt
#
# Needs apt-ftparchive (apt-utils), dpkg-deb and gpg. APT_URL is the public address of the repository,
# APT_GPG_PASSPHRASE the passphrase of the signing key if it has one.

set -eu

repo=$1
shift
suite=stable
component=main
name=filebot-pro
url=${APT_URL:?APT_URL is not set}

rm -rf "$repo"
mkdir -p "$repo/pool/$component"
for deb in "$@"; do
    cp "$deb" "$repo/pool/$component/"
done
architectures=$(for deb in "$repo/pool/$component"/*.deb; do dpkg-deb --field "$deb" Architecture; done | sort -u)

cd "$repo"
for arch in $architectures; do
    mkdir -p "dists/$suite/$component/binary-$arch"
    apt-ftparchive --arch "$arch" packages "pool/$component" > "dists/$suite/$component/binary-$arch/Packages"
    gzip -9 -n -k "dists/$suite/$component/binary-$arch/Packages"
done
rm -r pool

apt-ftparchive \
    -o APT::FTPArchive::Release::Origin="FileBot Pro" \
    -o APT::FTPArchive::Release::Label="FileBot Pro" \
    -o APT::FTPArchive::Release::Suite="$suite" \
    -o APT::FTPArchive::Release::Codename="$suite" \
    -o APT::FTPArchive::Release::Architectures="$(echo $architectures)" \
    -o APT::FTPArchive::Release::Components="$component" \
    -o APT::FTPArchive::Release::Description="FileBot Pro packages" \
    release "dists/$suite" > Release
mv Release "dists/$suite/Release"

key=$(gpg --batch --list-secret-keys --with-colons | awk -F: '$1 == "fpr" { print $10; exit }')
[ -n "$key" ] || { echo "No secret key in the gpg keyring" >&2; exit 1; }
sign() {
    if [ -n "${APT_GPG_PASSPHRASE:-}" ]; then
        gpg --batch --yes --pinentry-mode loopback --passphrase "$APT_GPG_PASSPHRASE" --local-user "$key" --digest-algo SHA512 "$@"
    else
        gpg --batch --yes --local-user "$key" --digest-algo SHA512 "$@"
    fi
}
sign --clearsign --output "dists/$suite/InRelease" "dists/$suite/Release"
sign --armor --detach-sign --output "dists/$suite/Release.gpg" "dists/$suite/Release"

gpg --batch --armor --export "$key" > "$name.asc"
cat > "$name.sources" <<EOF
Types: deb
URIs: $url
Suites: $suite
Components: $component
Signed-By: /etc/apt/keyrings/$name.asc
EOF

echo "Indexes signed with key $key:"
find . -type f | sort
cat "dists/$suite/Release"
