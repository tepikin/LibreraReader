package com.foobnix.ext;

import android.content.Context;
import android.os.Environment;
import android.support.v4.util.Pair;

import com.foobnix.android.utils.LOG;
import com.foobnix.mobi.parser.IOUtils;
import com.foobnix.pdf.info.ExtUtils;
import com.foobnix.sys.ArchiveEntry;
import com.foobnix.sys.ZipArchiveInputStream;

import net.lingala.zip4j.model.FileHeader;

import org.ebookdroid.BookType;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class CacheZipUtils {
    private static final int BUFFER_SIZE = 16 * 1024;

    public enum CacheDir {
        ZipApp("ZipApp"), //
        ZipService("ZipService"); //

        private final String type;

        private CacheDir(String type) {
            this.type = type;
        }

        public static File parent;

        public String getType() {
            return type;
        }

        public static void createCacheDirs() {
            for (CacheDir folder : values()) {
                File root = new File(parent, folder.getType());
                if (!root.exists()) {
                    root.mkdirs();
                }
            }
        }

        public void removeCacheContent() {
            try {
                removeFiles(getDir().listFiles());
            } catch (Exception e) {
                LOG.e(e);
            }
        }

        public File getDir() {
            return new File(parent, type);
        }

    }

    public static File CACHE_BOOK_DIR;
    public static File CACHE_WEB;
    public static File CACHE_RECENT;
    public static File ATTACHMENTS_CACHE_DIR;
    public static final Lock cacheLock = new ReentrantLock();

    public static void init(Context c) {
        File externalCacheDir = c.getExternalCacheDir();
        if (externalCacheDir == null) {
            externalCacheDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        }
        CacheDir.parent = externalCacheDir;

        CACHE_BOOK_DIR = new File(externalCacheDir, "Book");
        ATTACHMENTS_CACHE_DIR = new File(externalCacheDir, "Attachments");
        CACHE_WEB = new File(externalCacheDir, "WEB");
        CACHE_RECENT = new File(externalCacheDir, "Recent");

        CacheZipUtils.createAllCacheDirs();
        CacheDir.createCacheDirs();
    }

    public static void createAllCacheDirs() {
        if (!CACHE_BOOK_DIR.exists()) {
            CACHE_BOOK_DIR.mkdirs();
        }
        if (!ATTACHMENTS_CACHE_DIR.exists()) {
            ATTACHMENTS_CACHE_DIR.mkdirs();
        }
        if (!CACHE_WEB.exists()) {
            CACHE_WEB.mkdirs();
        }
    }

    public static class FilerByDate implements Comparator<File> {
        @Override
        public int compare(final File lhs, final File rhs) {
            return new Long(lhs.lastModified()).compareTo(new Long(rhs.lastModified()));
        }
    }

    public static void clearBookDir() {
        List<File> asList = Arrays.asList(CACHE_BOOK_DIR.listFiles());

        int cacheSize = 3;
        if (asList.size() <= cacheSize) {
            return;
        }

        Collections.sort(asList, new FilerByDate());

        for (int i = cacheSize; i < asList.size(); i++) {
            File file = asList.get(i);
            LOG.d("Remove file", file.getName());
            file.delete();
        }
    }

    public static void removeFiles(File[] files) {
        try {
            if (files == null) {
                return;
            }
            for (File file : files) {
                if (file != null) {
                    file.delete();
                    LOG.d("removeFile", file);
                }
            }
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    public static void removeFiles(File[] files, File exept) {
        try {
            if (files == null || exept == null) {
                return;
            }
            for (File file : files) {
                if (file != null && !file.getName().startsWith(exept.getName())) {
                    file.delete();
                }
            }
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    public static Pair<Boolean, String> isSingleAndSupportEntry(String file) {
        try {
            net.lingala.zip4j.core.ZipFile zp = new net.lingala.zip4j.core.ZipFile(file);
            List<FileHeader> fileHeaders = zp.getFileHeaders();
            int count = 0;
            FileHeader last = null;
            for (FileHeader h : fileHeaders) {
                if (h.isDirectory()) {
                    continue;
                }
                count++;
                last = h;
            }
            if (count == 1 && last != null) {
                String name = last.getFileName();
                return new Pair<Boolean, String>(BookType.isSupportedExtByPath(name), name);
            }
            return new Pair<Boolean, String>(false, "");
        } catch (Exception e) {
            return new Pair<Boolean, String>(false, "");
        }
    }

    public static Pair<Boolean, String> isSingleAndSupportEntry(InputStream is) {
        String name = "";
        try {

            ZipInputStream zipInputStream = new ZipInputStream(is);
            boolean find = false;
            ZipEntry nextEntry = null;

            while ((nextEntry = zipInputStream.getNextEntry()) != null) {
                if (nextEntry.isDirectory()) {
                    continue;
                }
                name = nextEntry.getName();
                if (find) {
                    zipInputStream.close();
                    return new Pair<Boolean, String>(false, "");
                }
                find = true;
            }
            zipInputStream.close();
        } catch (Exception e) {
            LOG.e(e);
        }
        return new Pair<Boolean, String>(BookType.isSupportedExtByPath(name), name);
    }

    public static class UnZipRes {
        public String originalPath;
        public String unZipPath;
        public String entryName;

        public UnZipRes(String originalPath, String unZipPath, String entryName) {
            this.originalPath = originalPath;
            this.unZipPath = unZipPath;
            this.entryName = entryName;
        }
    }

    public static UnZipRes extracIfNeed(String path, CacheDir folder) {
        return extracIfNeed(path, folder, -1);

    }

    public static UnZipRes extracIfNeed(String path, CacheDir folder, long salt) {
        if (!path.endsWith(".zip")) {
            return new UnZipRes(path, path, null);
        }

        folder.removeCacheContent();

        try {
            if (!isSingleAndSupportEntry(path).first) {
                return new UnZipRes(path, path, null);
            }
            ZipArchiveInputStream zipInputStream = new ZipArchiveInputStream(path);

            ArchiveEntry nextEntry = null;
            while ((nextEntry = zipInputStream.getNextEntry()) != null) {
                if (BookType.isSupportedExtByPath(nextEntry.getName())) {
                    String name = nextEntry.getName();
                    if (salt != -1) {
                        name = salt + name;
                    }
                    File file = new File(folder.getDir(), name);
                    BufferedOutputStream fileOutputStream = new BufferedOutputStream(new FileOutputStream(file));
                    IOUtils.copy(zipInputStream, fileOutputStream);
                    LOG.d("Unpack archive", file.getPath());

                    zipInputStream.close();

                    return new UnZipRes(path, file.getPath(), nextEntry.getName());
                }
            }
            zipInputStream.close();
        } catch (Exception e) {
            LOG.e(e);
        }
        return new UnZipRes(path, path, null);
    }

    public static boolean extractArchive(File fromFile, File toDir) {
        try {
            LOG.d("extractArchive From:", fromFile);
            LOG.d("extractArchive To:  ", toDir);
            ZipArchiveInputStream zipInputStream = new ZipArchiveInputStream(fromFile.getPath());

            ArchiveEntry nextEntry = null;
            while ((nextEntry = zipInputStream.getNextEntry()) != null) {
                if (nextEntry.isDirectory()) {
                    continue;
                }
                String name = ExtUtils.getFileName(nextEntry.getName());
                File file = new File(toDir, name);
                LOG.d("extractArchive", file.getName());
                BufferedOutputStream fileOutputStream = new BufferedOutputStream(new FileOutputStream(file));
                IOUtils.copy(zipInputStream, fileOutputStream);
            }
            zipInputStream.close();
        } catch (Exception e) {
            LOG.e(e);
            return false;
        }

        return true;
    }

    public static byte[] getEntryAsByte(InputStream zipInputStream) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        byte[] bytesIn = new byte[BUFFER_SIZE];
        int read = 0;
        while ((read = zipInputStream.read(bytesIn)) != -1) {
            out.write(bytesIn, 0, read);
        }
        out.close();
        return out.toByteArray();
    }



    static public void zipFolder(String srcFolder, String destZipFile) throws Exception {
        ZipOutputStream zip = null;
        FileOutputStream fileWriter = null;

        fileWriter = new FileOutputStream(destZipFile);
        zip = new ZipOutputStream(fileWriter);
        zip.setLevel(0);

        addFolderToZip("", srcFolder, zip);
        zip.flush();
        zip.close();
    }

    static private void addFileToZip(String path, String srcFile, ZipOutputStream zip) throws Exception {

        File folder = new File(srcFile);
        if (folder.isDirectory()) {
            addFolderToZip(path, srcFile, zip);
        } else {
            byte[] buf = new byte[BUFFER_SIZE];
            int len;
            FileInputStream in = new FileInputStream(srcFile);
            zip.putNextEntry(new ZipEntry(path + "/" + folder.getName()));
            while ((len = in.read(buf)) > 0) {
                zip.write(buf, 0, len);
            }
            in.close();
        }
    }

    static private void addFolderToZip(String path, String srcFolder, ZipOutputStream zip) throws Exception {
        File folder = new File(srcFolder);

        for (String fileName : folder.list()) {
            if (path.equals("")) {
                addFileToZip(folder.getName(), srcFolder + "/" + fileName, zip);
            } else {
                addFileToZip(path + "/" + folder.getName(), srcFolder + "/" + fileName, zip);
            }
        }
    }

    public static void deleteDir(File file) {
        File[] contents = file.listFiles();
        if (contents != null) {
            for (File f : contents) {
                deleteDir(f);
            }
        }
        file.delete();
    }

    public static void copyFile(File source, File dest) throws IOException {
        InputStream is = null;
        OutputStream os = null;
        try {
            is = new FileInputStream(source);
            os = new FileOutputStream(dest);
            byte[] buffer = new byte[BUFFER_SIZE];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
        } finally {
            is.close();
            os.close();
        }
    }

    public static void copyFile(InputStream is, File dest) throws IOException {
        OutputStream os = null;
        try {
            os = new FileOutputStream(dest);
            byte[] buffer = new byte[BUFFER_SIZE];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
        } finally {
            is.close();
            if (os != null) {
                os.close();
            }
        }
    }
}
