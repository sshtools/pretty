#!/bin/bash

for p in {0..255} ; do
	echo -en "\e]11;${p}\e\\ "
	sleep 0.05s
done