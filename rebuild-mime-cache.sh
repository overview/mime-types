#!/bin/bash

set -x

[ -f shared-mime-info-1.10.tar.xz ] || curl -o - --location http://freedesktop.org/~hadess/shared-mime-info-1.10.tar.xz | tar Jxf -
(cd ./shared-mime-info-1.10 && ./configure && make)
mkdir -p d/packages
cp shared-mime-info-1.10/freedesktop.org.xml d/packages/
cp extra-mime-info.xml d/packages/
shared-mime-info-1.10/update-mime-database d/
cp d/mime.cache src/main/resources/
