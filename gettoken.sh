#!/bin/bash
set -e
#RED='\033[0;31m'
#NORMAL='\033[0m'

myip=
while IFS=$': \t' read -a line ;do
    [ -z "${line%inet}" ] && ip=${line[${#line[1]}>4?1:2]} &&
        [ "${ip#127.0.0.1}" ] && myip=$ip
  done< <(LANG=C /sbin/ifconfig)


if [ -z "${myip}" ]; then
   myip=127.0.0.1
fi
if [ -z "${1}" ]; then
#printf "${RED}Getting OAuth2 token from Keycloak (includes access_token, refresh_token, etc):${NORMAL}\n"
KEYCLOAK_RESPONSE=`curl -s -X POST http://${myip}:8180/auth/realms/genny/protocol/openid-connect/token  -H "Content-Type: application/x-www-form-urlencoded" -d 'username=user1' -d 'password=password1' -d 'grant_type=password' -d 'client_id=curl'  -d 'client_secret=056b73c1-7078-411d-80ec-87d41c55c3b4'`
#printf "$KEYCLOAK_RESPONSE \n\n"

#printf "${RED}Parsing access_token field, as we don't need the other elements:${NORMAL}\n"
ACCESS_TOKEN=`echo "$KEYCLOAK_RESPONSE" | jq -r '.access_token'`
printf "${ACCESS_TOKEN} \n\n"
fi


