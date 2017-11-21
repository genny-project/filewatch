#!/bin/bash

if [ -z "${1}" ]; then
   version="latest"
else
   version="${1}"
fi

docker build --no-cache -f Dockerfile -t gennyproject/filewatch:${version} .
