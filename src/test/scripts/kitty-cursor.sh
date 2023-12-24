#!/bin/bash


getreply() {
    WAS=$(stty -g)
	stty raw -echo min 0
	echo -en "$*" > /dev/tty
	IFS="" read -e -s -d '\007' -r REPLY
	stty ${WAS}
	echo "${REPLY}"|cut -c1-
}

if [ "$1" = "--pop" ] ; then
	echo -en "\e]22;<\e\\"
elif [ "$1" = "--set" ] ; then
	echo -en "\e]22;=${2}\e\\"
elif [ "$1" = "--push" ] ; then
	echo -en "\e]22;>${2}\e\\"
elif [ "$1" = "--reset" ] ; then
	echo -en "\e]22;\e\\"
elif [ -n "$1" ] ; then
	echo -en "\e]22;${1}\e\\"
else
	echo -en "\e]22;?__current__\e\\"
	getreply|cat -v
fi