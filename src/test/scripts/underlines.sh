#!/bin/bash

function lines() {
	echo -e "\e[4mNormal Underline\e[24m ... and plain"
	echo -e "\e[21mDouble Underline\e[24m ... and plain"
	echo -e "\e[4:0mNo underline (kitty)\e[4:0m ... and plain"
	echo -e "\e[4:1mNormal underline (kitty)\e[4:0m ... and plain"
	echo -e "\e[4:2mDouble underline (kitty)\e[4:0m ... and plain"
	echo -e "\e[4:3mCurly underline (kitty)\e[4:0m ... and plain"
	echo -e "\e[4:4mDotted underline (kitty)\e[4:0m ... and plain"
	echo -e "\e[4:5mDashed underline (kitty)\e[4:0m ... and plain"
}


function collines() {
	echo -en "\e[58;5;${1}m"
	lines
	echo -en "\e[m"
}

lines
for p in {0..255..25} ; do
	echo
	collines $p
done