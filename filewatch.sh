#!/bin/bash

if [ -z "${1}" ];then
  echo "Usage: ./filewatch.sh <watched dir>" 
  exit
else
echo "watched dir path supplied... $1"
  WATCHDIR=$1
fi


if [ -z "${2}" ];then
echo "No ip supplied, determining local host ip ...."
myip=
while IFS=$': \t' read -a line ;do
    [ -z "${line%inet}" ] && ip=${line[${#line[1]}>4?1:2]} &&
        [ "${ip#127.0.0.1}" ] && myip=$ip
  done< <(LANG=C /sbin/ifconfig)


if [ -z "${myip}" ]; then
   myip=127.0.0.1
fi
echo "Hostip identified as ${myip}"
else
echo "ip supplied... $2"
myip=$2

fi

TOKEN=$(./gettoken.sh )


java -jar target/filewatch-0.0.1-SNAPSHOT-jar-with-dependencies.jar -t ${TOKEN} -f ${WATCHDIR} -a "http://${myip}:8088/api/data"

