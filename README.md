![Logo](https://raw.githubusercontent.com/foobnix/LirbiReader/master/logo.jpg)

# Librera Reader

Librera Reader is an e-book reader for Android devices; 
it supports the following formats: PDF, EPUB, MOBI, DjVu, FB2, TXT, RTF, AZW, AZW3, HTML, CBZ, CBR, and OPDS Catalogs

Web: [http://librera.mobi/](http://librera.mobi/)

Android Play market apps:

[Librera](https://play.google.com/store/apps/details?id=com.foobnix.pdf.reader)

[Librera PRO](https://play.google.com/store/apps/details?id=com.foobnix.pro.pdf.reader)

[Arhive .apk](http://archive.librera.mobi)

[Beta(latest) .apk](http://beta.librera.mobi)

Application fonts (**fonts.zip** download to internal sd card, to [Downloads] folder)
[link1](https://github.com/foobnix/LirbiReader/tree/master/Builder/fonts) 
[link2](https://www.dropbox.com/home/FREE_PDF_APK/testing)

[Telegram](https://t.me/LibreraReader)

## Build Librera

~~~~
sudo apt-get install mesa-common-dev libxcursor-dev  libxrandr-dev libxinerama-dev libglu1-mesa-dev libxi-dev pkg-config

/Builder/link_to_mupdf_1.11.sh (Change the paths to mupdf and jniLibs folders)
./gradlew assebleLibrera
~~~~

## Librera depends on

MuPDF - (AGPL License) https://mupdf.com/downloads/archive/ (mupdf-1.11-source.tar.xz)

MuPDF changed source ./LibreraReader/Builder/jni-1.11/

* EbookDroid
* djvulibre
* hpx
* junrar
* Universal Image Loader
* libmobi
* commons-compress
* eventbus
* greendao
* jsoup
* juniversalchardet
* commons-compress
* okhttp3
* okhttp-digest
* okio
* rtfparserkit
* java-mammoth

Librera is distributed under the GPL

## License

See the [LICENSE](LICENSE.txt) file for license rights and limitations (GPL v.3).
