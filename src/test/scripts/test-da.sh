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

#getreply TEST

DA3=$(getreply "\e[=c")
echo
echo "DA3: "$(echo "${DA3}"|cat -v)
