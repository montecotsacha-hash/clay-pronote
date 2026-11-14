#!/usr/bin/env bash
# Échoue si un secret ou une donnée personnelle est présent dans les fichiers
# suivis par git. Utilisé par .github/workflows/privacy.yml à chaque push, et
# utilisable à la main : bash scripts/check-privacy.sh
set -uo pipefail

root=$(git rev-parse --show-toplevel) || { echo "hors dépôt git" >&2; exit 1; }
cd "$root" || exit 1

patterns=.github/privacy-patterns.txt
script=scripts/check-privacy.sh
# L'identifiant GitHub public est légitime partout (URL du dépôt) : il contient
# le prénom sans l'exposer comme donnée personnelle, il est donc retiré des
# lignes avant comparaison.
handle=montecotsacha-hash
# Sorties de build du site : JS minifié, généré, jamais édité à la main.
generated=':(exclude)deploy/assets'
max_bytes=20000000
fail=0

report() { printf '::error::%s\n' "$1"; fail=1; }
section() { printf '\n%s\n' "$1"; }

if [ ! -f "$patterns" ]; then
  echo "motifs introuvables : $patterns" >&2
  exit 1
fi

section "1. Fichiers sensibles suivis"
while IFS= read -r file; do
  case "$file" in
    *.jks | *.keystore | *.p12 | *.pem | *.p8 | *.pfx | *.kdbx | *.aab.asc | *.jks.enc)
      report "fichier de clé suivi : $file" ;;
    keystore.properties | */.env | .env | .dev.vars | */.dev.vars)
      report "fichier d'environnement ou de mots de passe suivi : $file" ;;
  esac
done < <(git ls-files)

section "2. Motifs du fichier $patterns"
scan() {
  local type=$1 label=$2 combined hits line probe
  combined=$(awk -F'|' -v t="$type" '$1 == t { print $2 }' "$patterns" | paste -sd'|' -)
  [ -z "$combined" ] && return 0
  hits=$(git grep -nI -i -E -- "$combined" -- . ":(exclude)$patterns" ":(exclude)$script" "$generated" 2>/dev/null || true)
  while IFS= read -r line; do
    [ -z "$line" ] && continue
    probe=${line//$handle/}
    if printf '%s' "$probe" | grep -q -i -E -- "$combined"; then
      report "$label : $line"
    fi
  done <<<"$hits"
}
scan secret "secret exposé"
scan person "donnée personnelle"

section "3. Taille des fichiers suivis (${max_bytes} octets maximum)"
while IFS= read -r file; do
  [ -f "$file" ] || continue
  size=$(wc -c <"$file" 2>/dev/null | tr -d ' ')
  if [ -n "$size" ] && [ "$size" -gt "$max_bytes" ]; then
    report "fichier trop volumineux suivi : $file ($((size / 1048576)) Mo)"
  fi
done < <(git ls-files)

printf '\n'
if [ "$fail" -ne 0 ]; then
  echo "ÉCHEC : retire les éléments signalés ci-dessus avant de commiter."
  exit 1
fi
echo "OK : aucun secret ni donnée personnelle détecté dans les fichiers suivis."
