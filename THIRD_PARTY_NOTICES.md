# Third-party notices

FileBot Pro is released under the [GNU General Public License v3.0](LICENSE.md). It contains and is based on the
third-party work listed below, each under its own license.

## FileBot

FileBot Pro is a fork of [FileBot](https://www.filebot.net) 4.7.9 by Reinhard Pointner, the last release published
as free software under the GNU General Public License. FileBot Pro is an independent project and is not affiliated
with or endorsed by FileBot or its author.

## Built-in scripts

The `fn:` scripts in [`src/main/resources/dev/davamu/filebotpro/scripts`](src/main/resources/dev/davamu/filebotpro/scripts)
are based on the [FileBot scripts](https://github.com/filebot/scripts) (script bundle r836, 2022-08-15), licensed
under the GNU General Public License v3.0. Changes for FileBot Pro: they are bundled instead of downloaded, the
license check, MyEpisodes updates, e-mail reports and FileBot branding were removed, and series default to
`TheMovieDB::TV`.

## Detection data

The lists in [`src/main/resources/dev/davamu/filebotpro/data`](src/main/resources/dev/davamu/filebotpro/data) are
built from:

- [Wikidata](https://www.wikidata.org) (movie, series and subtitle titles), dedicated to the public domain under
  [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/).
- The [anime-offline-database](https://github.com/manami-project/anime-offline-database) by manami-project (anime
  titles and pictures), made available under the [Open Database License 1.0](https://opendatacommons.org/licenses/odbl/1.0/);
  its contents under the [Database Contents License 1.0](https://opendatacommons.org/licenses/dbcl/1.0/). The derived
  `anidb.txt.xz` and `anidb-pictures.txt.xz` are made available under the same license, see `anidb.LICENSE.txt`.
- [TheDiscDB](https://thediscdb.com) ([data](https://github.com/TheDiscDb/data)), the episodes, movies and extras of the titles of
  Blu-ray and DVD discs in `discdb.txt.xz`, under the [MIT License](https://opensource.org/license/mit), see
  `discdb.LICENSE.txt`.

## Libraries

Bundled with every build (licenses as declared by each project):

| Component | Version | License |
| --- | --- | --- |
| [args4j:args4j](http://args4j.kohsuke.org/) | 2.37 | MIT License |
| [ca.weblite:java-objc-bridge](https://github.com/shannah/Java-Objective-C-Bridge) | 1.2 | Apache License, Version 2.0 |
| [com.adobe.xmp:xmpcore](https://www.adobe.com/devnet/xmp/library/eula-xmp-library-java.html) | 6.1.11 | The BSD 3-Clause License (BSD3) |
| [com.cedarsoftware:json-io](https://github.com/jdereg/json-io) | 4.14.1 | The Apache Software License, Version 2.0 |
| [com.dampcake:bencode](http://github.com/dampcake/bencode) | 1.4.2 | The Apache Software License, Version 2.0 |
| [com.drewnoakes:metadata-extractor](https://drewnoakes.com/code/exif/) | 2.21.0 | The Apache Software License, Version 2.0 |
| [com.fifesoft:rsyntaxtextarea](https://github.com/bobbylight/rsyntaxtextarea) | 3.6.3 | BSD-3-Clause |
| [com.formdev:flatlaf](https://github.com/JFormDesigner/FlatLaf) | 3.7.2 | The Apache License, Version 2.0 |
| [com.formdev:flatlaf-extras](https://github.com/JFormDesigner/FlatLaf) | 3.7.2 | The Apache License, Version 2.0 |
| [com.formdev:flatlaf-fonts-inter](https://github.com/JFormDesigner/FlatLaf) | 4.1 | SIL OPEN FONT LICENSE Version 1.1 / The Apache License, Version 2.0 |
| [com.formdev:flatlaf-fonts-jetbrains-mono](https://github.com/JFormDesigner/FlatLaf) | 2.304 | SIL OPEN FONT LICENSE Version 1.1 / The Apache License, Version 2.0 |
| [com.github.ben-manes.caffeine:caffeine](https://github.com/ben-manes/caffeine) | 3.3.0 | Apache License, Version 2.0 |
| [com.github.mpkorstanje:simmetrics-core](https://github.com/mpkorstanje/simmetrics) | 4.1.1 | The Apache Software License, Version 2.0 |
| [com.glazedlists:glazedlists](http://www.glazedlists.com) | 1.11.0 | GNU Lesser General Public License / Mozilla Public License Version 1.1 |
| com.google.errorprone:error_prone_annotations | 2.50.0 | Apache 2.0 |
| [com.google.guava:failureaccess](https://github.com/google/guava) | 1.0.3 | Apache License, Version 2.0 |
| [com.google.guava:guava](https://github.com/google/guava) | 33.7.2-jre | Apache License, Version 2.0 |
| [com.google.guava:listenablefuture](https://github.com/google/guava) | 9999.0-empty-to-avoid-conflict-with-guava | The Apache Software License, Version 2.0 |
| [com.google.j2objc:j2objc-annotations](https://github.com/google/j2objc/) | 3.1 | Apache License, Version 2.0 |
| [com.googlecode.lanterna:lanterna](https://github.com/mabe02/lanterna) | 3.1.3 | GNU Lesser General Public License |
| [com.ibm.icu:icu4j](https://icu.unicode.org/) | 78.3 | Unicode-3.0 |
| [com.intellij:annotations](http://www.jetbrains.org) | 12.0 | Apache License 2 |
| [com.miglayout:miglayout-core](http://www.miglayout.com/) | 11.4.2 | BSD |
| [com.miglayout:miglayout-swing](http://www.miglayout.com/) | 11.4.2 | BSD |
| [com.optimaize.languagedetector:language-detector](https://github.com/optimaize/language-detector) | 0.6 | Apache 2 |
| com.sun.istack:istack-commons-runtime | 4.1.2 | Eclipse Distribution License - v 1.0 |
| [commons-codec:commons-codec](http://commons.apache.org/proper/commons-codec/) | 1.10 | Apache License, Version 2.0 |
| [commons-io:commons-io](https://commons.apache.org/proper/commons-io/) | 2.22.0 | Apache-2.0 |
| [commons-logging:commons-logging](https://commons.apache.org/proper/commons-logging/) | 1.3.4 | Apache-2.0 |
| [jakarta.activation:jakarta.activation-api](https://github.com/jakartaee/jaf-api) | 2.1.4 | EDL 1.0 |
| [jakarta.xml.bind:jakarta.xml.bind-api](https://github.com/jakartaee/jaxb-api) | 4.0.5 | Eclipse Distribution License - v 1.0 |
| [net.arnx:jsonic](http://jsonic.sourceforge.jp/) | 1.2.11 | The Apache Software License, Version 2.0 |
| [net.java.dev.jna:jna](https://github.com/java-native-access/jna) | 5.19.1 | Apache-2.0 / LGPL-2.1-or-later |
| [net.java.dev.jna:jna-platform](https://github.com/java-native-access/jna) | 5.19.1 | Apache-2.0 / LGPL-2.1-or-later |
| [net.sf.sevenzipjbinding:sevenzipjbinding](http://sevenzipjbind.sourceforge.net/) | 23.01-2.2 | LGPL / unRAR restriction (see below) |
| [net.sf.sevenzipjbinding:sevenzipjbinding-all-platforms](http://sevenzipjbind.sourceforge.net/) | 23.01-2.2 | LGPL / unRAR restriction (see below) |
| [one.util:streamex](https://github.com/amaembo/streamex) | 0.9.0 | Apache License, Version 2.0 |
| [org.apache.commons:commons-lang3](https://commons.apache.org/proper/commons-lang/) | 3.17.0 | Apache-2.0 |
| [org.apache.commons:commons-vfs2](https://commons.apache.org/proper/commons-vfs/) | 2.10.0 | Apache-2.0 |
| [org.apache.groovy:groovy](https://groovy-lang.org) | 4.0.33 | The Apache Software License, Version 2.0 |
| [org.apache.groovy:groovy-datetime](https://groovy-lang.org) | 4.0.33 | The Apache Software License, Version 2.0 |
| [org.apache.groovy:groovy-dateutil](https://groovy-lang.org) | 4.0.33 | The Apache Software License, Version 2.0 |
| [org.apache.groovy:groovy-json](https://groovy-lang.org) | 4.0.33 | The Apache Software License, Version 2.0 |
| [org.apache.groovy:groovy-jsr223](https://groovy-lang.org) | 4.0.33 | The Apache Software License, Version 2.0 |
| [org.apache.groovy:groovy-nio](https://groovy-lang.org) | 4.0.33 | The Apache Software License, Version 2.0 |
| [org.apache.groovy:groovy-templates](https://groovy-lang.org) | 4.0.33 | The Apache Software License, Version 2.0 |
| [org.apache.groovy:groovy-xml](https://groovy-lang.org) | 4.0.33 | The Apache Software License, Version 2.0 |
| [org.eclipse.angus:angus-activation](https://github.com/eclipse-ee4j/angus-activation) | 2.0.3 | EDL 1.0 |
| [org.glassfish.jaxb:jaxb-core](https://eclipse-ee4j.github.io/jaxb-ri/) | 4.0.9 | Eclipse Distribution License - v 1.0 |
| [org.glassfish.jaxb:jaxb-runtime](https://eclipse-ee4j.github.io/jaxb-ri/) | 4.0.9 | Eclipse Distribution License - v 1.0 |
| [org.glassfish.jaxb:txw2](https://eclipse-ee4j.github.io/jaxb-ri/) | 4.0.9 | Eclipse Distribution License - v 1.0 |
| [org.imgscalr:imgscalr-lib](http://www.thebuzzmedia.com/software/imgscalr-java-image-scaling-library/) | 4.2 | ASF 2.0 |
| [org.jsoup:jsoup](https://jsoup.org/) | 1.23.2 | The MIT License |
| [org.jspecify:jspecify](https://jspecify.dev/) | 1.0.1 | The Apache License, Version 2.0 |
| [org.slf4j:slf4j-api](http://www.slf4j.org) | 2.0.17 | MIT |
| [org.slf4j:slf4j-nop](http://www.slf4j.org) | 2.0.17 | MIT |
| [org.tukaani:xz](https://tukaani.org/xz/java.html) | 1.12 | 0BSD |

The Inter and JetBrains Mono fonts inside the `flatlaf-fonts-*` packages are licensed under the SIL Open Font
License 1.1.

## Native tools and Java runtime

- The Windows builds include [MediaInfo](https://mediaarea.net/en/MediaInfo) 26.10 (BSD-2-Clause),
  [Chromaprint](https://acoustid.org/chromaprint) 1.6.1 `fpcalc` (MIT / LGPL-2.1), [MKVToolNix](https://mkvtoolnix.download/) 102.0 `mkvpropedit`
  (GPL-2.0) and [AtomicParsley](https://github.com/wez/atomicparsley) 20240608 (GPL-2.0) as separate programs. The license
  texts of `fpcalc` and AtomicParsley are in `native/licenses`; the MediaInfo and MKVToolNix texts are not copied there
  yet, see their project pages.
- The self-contained builds (portable and installers) bundle a Java runtime; its license files are in
  `runtime/legal`.
- The Synology packages (`.spk`) bundle the [Eclipse Temurin](https://adoptium.net/) 25 Java runtime (GPL-2.0 with
  Classpath Exception) built from the pinned release in `build.gradle.kts`.
- The Docker images are based on `eclipse-temurin:25-jre` (Ubuntu) and install MediaInfo (`libmediainfo0v5`, BSD-2-Clause),
  `libchromaprint-tools` (`fpcalc`), MKVToolNix, AtomicParsley (GPL-2.0) and 7-Zip (LGPL / unRAR restriction) from the Ubuntu
  repositories. The `gui` image adds Xvfb, openbox, x11vnc (GPL-2.0), noVNC (MPL-2.0) and websockify (LGPL-3.0). Their
  versions are those of the Ubuntu release of the base image (`filebot-pro -script fn:sysinfo` prints them); each package
  keeps its license text in `/usr/share/doc/<package>/copyright` inside the image.

## Online services

Metadata and artwork come from the services you choose (TheMovieDB, TheTVDB, AniDB, TVmaze, OMDb, OpenSubtitles,
AcoustID, Fanart.tv, TheXEM, Shooter). Photo locations are looked up with Google Geocoding. Their data is subject to
each service's terms; FileBot Pro is not endorsed or certified by
any of them.

This product uses the TMDB API but is not endorsed or certified by [TMDB](https://www.themoviedb.org). The data sources
are credited in the app under **Help → About FileBot Pro**.

## 7-Zip-JBinding and unRAR

7-Zip-JBinding bundles the 7-Zip code, which includes the unRAR decoder. Its license forbids using the code to recreate
the RAR compression algorithm, a restriction that the GPL does not allow to add to GPL-licensed software. FileBot Pro
only reads RAR archives with it. Its compatibility with the GPL-3.0 has not been reviewed by a lawyer: whoever
redistributes FileBot Pro (packages, images, repositories) should check it for their case.
