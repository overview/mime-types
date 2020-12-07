#!/bin/bash

set -x

[ -f shared-mime-info-2.0.tar.xz ] || curl -o - --location https://gitlab.freedesktop.org/xdg/shared-mime-info/-/archive/2.0/shared-mime-info-2.0.tar.gz | tar zxf -
(cd ./shared-mime-info-2.0 && meson build && cd build && ninja src/update-mime-database data/freedesktop.org.xml)
mkdir -p d/packages
cp shared-mime-info-2.0/build/data/freedesktop.org.xml d/packages/
cp extra-mime-info.xml d/packages/
shared-mime-info-2.0/update-mime-database -V d/
cp d/mime.cache src/main/resources/
