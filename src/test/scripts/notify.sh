#!/bin/bash

if [ "$1" = "-k" ] ; then
	shift
	if [ "$1" = "-t" ] ; then
		shift
		title="$1"
		shift
	fi
	if [ -n "${title}" ] ; then
		echo -e "\e]99;i=1:d=0:p=title;${title}\e\\"
		echo -e "\e]99;i=1:d=1:p=body;${*}\e\\"
	else
		echo -e "\e]99;;${*}\e\\"
	fi
else
	echo -en "\e]9;${*}\e\\"
fi
