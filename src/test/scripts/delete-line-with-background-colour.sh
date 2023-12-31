#!/bin/bash
tput clear
tput home
echo -e "\E[37;44m\E[2J\c" 
for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20
do
    if [ "$i" != 1 ]
    then echo
    fi
    echo -n "$i"
done 
sleep 5
tput cup 20 0
echo -e "\E[M\c"
sleep 5
