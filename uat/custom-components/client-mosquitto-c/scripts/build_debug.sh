#!/bin/sh

rm -rf build && mkdir -p build

CXXFLAGS="-Wall -Wextra -g -O0" cmake -Bbuild -H.
cmake --build build -j 4 --target all
