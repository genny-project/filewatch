#!/bin/bash

echo "Passed parameters = " $@

if [[ $DEBUG == "TRUE" ]]; then 
   echo "Remote Debug on port 8787 True"; 
   java -jar app.jar  $@
else 
   echo "Debug is False"; 
   java -jar app.jar  $@
fi
