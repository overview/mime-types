How to release a new version
============================

One-time setup: Create a `~/.m2/settings.xml` that looks like this:

```xml
<settings>
  <servers>
    <server>
      <id>ossrh</id>
      <username>SONATYPE-JIRA-USERNAME</username>
      <password>SONATYPE-JIRA-PASSWORD</password>
    </server>
  </servers>
</settings>
```

Every time:

1. `mvn versions:set -DnewVersion=x.x.x`
2. `mvn clean deploy -P release`

How to support a new file type
==============================

The best way to support a new file type is to put it in `shared-mime-info`, the
open-source repository of all MIME types.

1. Submit a pull request to https://gitlab.freedesktop.org/xdg/shared-mime-info,
   making it support the new file type.
2. Wait for a new release of shared-mime-info.
3. Change the URL and version numbers in this repo's `rebuild-mime-cache.sh`.
4. Run `rebuild-mime-cache.sh` (on Linux -- requires some standard build tools).
5. For bonus points, add a test file to this repository (`src/test/resources/test/`)
   and test that it is detected correctly
   (`src/test/java/org/overviewproject/mime_types/MimeTypeDetectorTest.java`).
6. Submit a pull request.

Alternatively, you are welcome to request a volunteer to do this process, at
https://github.com/overview/mime-types/issues.

If you want to support a non-standard MIME type (just for your own project, not
for public consumption), please contribute to
https://github.com/overview/mime-types/issues/5.
