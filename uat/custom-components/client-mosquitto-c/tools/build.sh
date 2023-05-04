#!/bin/sh

mkdir -p build
CFLAG="-Wall -Wextra -g -O0" cmake -Bbuild -H.
cmake --build build  --target all
