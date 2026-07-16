#!/usr/bin/env sh
set -eu
mkdir -p backups
cp data/core-ai.db "backups/core-ai-$(date +%Y%m%d-%H%M%S).db"
