#!/bin/bash


function pals() {
	echo "8 Palette:"
	for p in {40..49} ; do
		echo -en "\e[${p}m "
	done
	echo -e "\e[m"
	echo
	echo
	
	echo "16 Palette:"
	for p in {40..49} ; do
		echo -en "\e[${p}m "
	done
	for p in {100..107} ; do
		echo -en "\e[${p}m "
	done
	echo -e "\e[m"
	echo
	echo
	
	echo "88 Palette:"
	for p in {0..88} ; do
		echo -en "\e[48;5;${p}m "
	done
	echo -e "\e[m"
	echo
	echo
	
	echo "256 Palette:"
	for p in {0..255} ; do
		echo -en "\e[48;5;${p}m "
	done
	echo -e "\e[m"
	echo
}

function rgbs() {
	echo "RGB (sample):"
	for r in {0..255..25} ; do
		for g in {0..255..25} ; do
			for b in {0..255..25} ; do
				echo -en "\e[48;2;${r};${g};${b}m "
			done
		done
	done
	echo -e "\e[m"

}

tput clear
pals
rgbs