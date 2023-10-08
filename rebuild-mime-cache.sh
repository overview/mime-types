#!/bin/bash

set -ex

VERSION="2.3"

[ -f shared-mime-info-$VERSION.tar.xz ] || curl -o - --location https://gitlab.freedesktop.org/xdg/shared-mime-info/-/archive/$VERSION/shared-mime-info-$VERSION.tar.gz | tar zxf -
(cd ./shared-mime-info-$VERSION && meson build && cd build && ninja src/update-mime-database data/freedesktop.org.xml)
mkdir -p d/packages
cp shared-mime-info-$VERSION/build/data/freedesktop.org.xml d/packages/
cp extra-mime-info.xml d/packages/
shared-mime-info-$VERSION/build/src/update-mime-database -V d/
cp d/mime.cache src/main/resources/
