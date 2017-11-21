#!/bin/bash

if [ -z "${1}" ]; then
   version="latest"
else
   version="${1}"
fi


docker push gennyproject/filewatch:"${version}"
docker tag  gennyproject/filewatch:"${version}"  gennyproject/filewatch:latest
docker push gennyproject/filewatch:latest

