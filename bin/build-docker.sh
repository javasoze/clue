#!/usr/bin/env bash

set -euo pipefail

bin=$(dirname "$0")
repo_root=$(cd "$bin/.."; pwd)

docker build -f "$repo_root/docker/Dockerfile" -t clue "$repo_root"
