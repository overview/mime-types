[![Maven](https://img.shields.io/maven-central/v/org.overviewproject/mime-types.svg)](https://repo1.maven.org/maven2/org/overviewproject/mime-types/)
[![License](http://img.shields.io/:license-apache-yellow.svg)](http://www.apache.org/licenses/LICENSE-2.0.html)
[![javadoc](https://javadoc.io/badge2/org.overviewproject/mime-types/javadoc.svg)](https://javadoc.io/doc/org.overviewproject/mime-types)


MIME Type Detector
==================

Ever see "application/octet-stream" or "text/plain" or "text/html"? Those are
called MIME types. ("MIME" stands for Multi-purpose Internet Mail Extensions.)

If your Java program has files and needs to figure out their MIME types, this
library will help.

Usage
=====

```java
import org.overviewproject.mime_types.MimeTypeDetector

// ...

File file = new File("foo.txt");
MimeTypeDetector detector = new MimeTypeDetector();
String mimeType = detector.detectMimeType(file); // "text/plain"

// ... or ...

InputStream stream = new ByteArrayInputStream("words".getBytes("utf-8"))
String mimeType = detector.detectMimeType("filename", stream); // "text/plain"
```

No library can figure out MIME types perfectly. But there's a standard, called
[shared-mime-info](https://freedesktop.org/wiki/Software/shared-mime-info/).
This library follows its algorithm to the letter.

You need two things to detect a file's type:

1. The file's name
2. The first few bytes of the file's content

The bytes aren't always needed; for speed, you can pass a callback instead of
the actual bytes, and this library will only invoke the callback when it's
absolutely necessary. There's an async version, too.

For a method that matches the files you have, read the
[MimeTypeDetector docs](https://javadoc.io/doc/org.overviewproject/mime-types/latest/org/overviewproject/mime_types/MimeTypeDetector.html).

Credits
=======

Big thanks to Medsea Business Solutions S.L. for most of this code. I merely
adjusted the API. (The original project at http://sourceforge.net/p/mime-util/
seems to be dead.)
