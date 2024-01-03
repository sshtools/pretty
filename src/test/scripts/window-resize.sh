#!/bin/bash

getreply() {
    WAS=$(stty -g)
	stty raw -echo min 0
	echo -en "$*" > /dev/tty
	IFS="" read -e -s -d '\007' -r REPLY
	stty ${WAS}
	echo "${REPLY}"|cut -c1-
}

tput clear

echo -en "\e[8;80;40t"

#getreply TEST

rep1=$(getreply "\e]10;?\007")
rep2=$(getreply "\e]11;?\007")
echo
echo "Reply1: "$(echo "$rep1"|cat -v)
echo "Reply2: "$(echo "$rep2"|cat -v)
