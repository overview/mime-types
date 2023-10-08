v2.0.0 - 2023-10-07
-------------------

* Upgrade to shared-mime-info v2.3.0
  [#18](https://github.com/overview/mime-types/issues/18)
  See https://gitlab.freedesktop.org/xdg/shared-mime-info/-/releases/2.3
  for changes. They may break expectations -- e.g., `.js` -> text/javascript

v1.0.4 - 2022-08-23
-------------------

* Fix regression that threw exception on empty filename
  [#16](https://github.com/overview/mime-types/pull/16)

v1.0.3 - 2022-08-16
-------------------

* Upgrade to shared-mime-info v2.2.0
  [#12](https://github.com/overview/mime-types/pull/12)
* Tweak code style and documentation.

v1.0.1 - 2020-12-08
-------------------

* Fix java.lang.NoSuchMethodError from Java versioning oddities
  [#10](https://github.com/overview/mime-types/issues/10)

v1.0.0 - 2020-12-08
-------------------

* Added NIO SPI FileTypeDetector [#8](https://github.com/overview/mime-types/pull/8)
* Updated `mime.cache` to use shared-mime-info v2.0.0
