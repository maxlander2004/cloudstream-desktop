#!/bin/sh
printf '%s\n' "protocol=https" "host=github.com" "path=/" "username=maxlander2004" "password=$(cat /tmp/.ghtoken)"
