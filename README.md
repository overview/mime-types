[![Maven](https://img.shields.io/maven-central/v/org.overviewproject/mime-types.svg)](https://repo1.maven.org/maven2/org/overviewproject/mime-types/)
[![License](http://img.shields.io/:license-apache-yellow.svg)](http://www.apache.org/licenses/LICENSE-2.0.html)

MIME Type Detector
==========================

Ever seen "application/octet-stream" or "text/plain" or "text/html"? Those are
called MIME types. ("MIME" stands for Multi-purpose Internet Mail Extensions.)

If your Java program has Files and needs to figure out MIME types, this
library will help.

Just do this:

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
shared-mime-info. This library follows it to the letter.

You need two things to detect a file's type:

1. The file's name
2. The first few bytes of the file's content

The bytes aren't always needed; for speed, you can pass a callback instead of
the actual bytes, and this library will only invoke the callback when it's
absolutely necessary. There's an async version, too.

##### CREDITS

Big thanks to Medsea Business Solutions S.L. for most of this code. I merely
adjusted the API. (The original project at http://sourceforge.net/p/mime-util/
seems to be dead.)
