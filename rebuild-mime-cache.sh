#!/bin/bash

set -x

[ -f shared-mime-info-1.13.1.tar.xz ] || curl -o - --location https://gitlab.freedesktop.org/xdg/shared-mime-info/uploads/5349e18c86eb96eee258a5c1f19122d0/shared-mime-info-1.13.1.tar.xz | tar Jxf -
(cd ./shared-mime-info-1.13.1 && ./configure && make)
mkdir -p d/packages
cp shared-mime-info-1.13.1/freedesktop.org.xml d/packages/
cp extra-mime-info.xml d/packages/
shared-mime-info-1.13.1/update-mime-database d/
cp d/mime.cache src/main/resources/
