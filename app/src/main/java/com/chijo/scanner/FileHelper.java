package com.chijo.scanner;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class FileHelper {

    private static String tempName = "";
    private static String tempDate = "";
    private static String tempPages = "";
    private static String tempArchived = "";

    private static final int BITMAP_DEFAULT_SCALE = 8;

    private FileHelper() {

    }

    public static void writeName(Document doc, String name) {
        tempName = name;
        tempDate = doc.getLastUpdatedDate();
        tempPages = doc.getPages();
        tempArchived = (doc.isArchived() ? "true" : "false");
        writeAll(doc);
    }

    public static void writeDate(Document doc, String date) {
        tempName = doc.getDocumentName();
        tempDate = date;
        tempPages = doc.getPages();
        tempArchived = (doc.isArchived() ? "true" : "false");
        writeAll(doc);
    }

    public static void writePages(Document doc, String pages) {
        tempName = doc.getDocumentName();
        tempDate = doc.getLastUpdatedDate();
        tempPages = pages;
        tempArchived = (doc.isArchived() ? "true" : "false");
        writeAll(doc);
    }

    public static void writeIsArchived(Document doc, String isArchived) {
        tempName = doc.getDocumentName();
        tempDate = doc.getLastUpdatedDate();
        tempPages = doc.getPages();
        tempArchived = isArchived;
        writeAll(doc);
    }

    public static void writeAll(Document doc, String name, String date, String pages, String isArchived) {
        tempName = name;
        tempDate = date;
        tempPages = pages;
        tempArchived = isArchived;
        writeAll(doc);
    }
    public static void writeAll(String path, String name, String date, String pages, String isArchived) {
        tempName = name;
        tempDate = date;
        tempPages = pages;
        tempArchived = isArchived;
        writeAll(path);
    }

    private static void writeAll(Document doc) {
        writeAll(doc.getPath());
    }

    private static void writeAll(String path) {
        File documentFolder = new File(path);
        if(!documentFolder.exists()) return;
        File[] files = documentFolder.listFiles();
        if(files == null) return;
        for(File f : files) {
            if(f.getName().equals("infoFile.txt")) {
                try {
                    new FileOutputStream(f.getPath()).close(); //delete file contents
                    OutputStream output = null;
                    try {
                        output = new FileOutputStream(f);
                        output.write((tempName + "\n").getBytes());
                        output.write((tempDate + "\n").getBytes());
                        output.write((tempPages + "\n").getBytes());
                        output.write(tempArchived.getBytes());
                    } finally {
                        if (output != null) {
                            output.close();
                        }
                    }
                } catch(FileNotFoundException e) {
                    e.printStackTrace();
                } catch(IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static boolean renameFile(String documentPath, String nameFrom, String nameTo) {
        File documentFolder = new File(documentPath);
        if(!documentFolder.exists()) return false;
        File[] files = documentFolder.listFiles();
        if(files == null) return false;
        boolean renamed = false;
        for(File f : files) {
            if(f.getName().equals(nameFrom)) {
                File to = new File(documentPath, nameTo);
                renamed = f.renameTo(to);
                break;
            }
        }
        return renamed;
    }

    public static boolean moveFile(String fromPath, String toPath, String fileName) {
        File fromFolder = new File(fromPath);
        File toFolder = new File(toPath);
        if(!fromFolder.exists() || !toFolder.exists()) return false;
        for(File f : fromFolder.listFiles()) {
            if (f.getName().equals(fileName)) {
                f.renameTo(new File(toPath + "/" + fileName));
                break;
            }
        }
        return true;
    }

    public static boolean copyFile(String fromPath, String toPath, String fileName) throws IOException {
        File fromFolder = new File(fromPath);
        File toFolder = new File(toPath);
        if(!fromFolder.exists() || !toFolder.exists()) return false;
        for(File f : fromFolder.listFiles()) {
            if (f.getName().equals(fileName)) {
                File copyTo = new File(toPath + "/" + fileName);

                FileChannel src = null;
                FileChannel dest = null;
                try {
                    src = new FileInputStream(f).getChannel();
                    dest = new FileOutputStream(copyTo).getChannel();
                    dest.transferFrom(src, 0, src.size());
                } finally {
                    if (src != null) src.close();
                    if (dest != null) dest.close();
                }
                break;
            }
        }
        return true;
    }

    public static boolean deleteFile(String documentPath, String deleteName) {
        File documentFolder = new File(documentPath);
        if(!documentFolder.exists()) return false;
        File[] files = documentFolder.listFiles();
        if(files == null) return false;
        boolean deleted = false;
        for(File f : files) {
            if(f.getName().equals(deleteName)) {
                f.delete();
                deleted = true;
                break;
            }
        }
        return deleted;
    }

    public static boolean deleteDocument(Document doc) {
        return deleteDocument(doc.getPath());
    }

    public static boolean deleteDocument(String path) {
        File documentFolder = new File(path);
        if(!documentFolder.exists()) return false;
        File[] files = documentFolder.listFiles();
        if(files == null) return false;
        //delete each file
        for(File f : files) {
            f.delete();
        }
        //delete directory
        documentFolder.delete();
        return true;
    }

    private static String removeExt(String filename) {
        int i = filename.lastIndexOf(".");
        if (i == -1) return filename;
        return filename.substring(0, i);
    }

    public static Document mergeDocuments(ArrayList<Document> docList) throws IOException {
        if (docList.size() < 2) return null;
        //copy d1 to new document
        Document d1 = docList.get(0);
        String doc1Path = d1.getPath();
        File doc1File = new File(doc1Path);
        if (!doc1File.exists()) return null;

        String copyDocPath = AppConstants.DOCUMENT_SAVE_LOCATION + System.currentTimeMillis();
        File f = new File(copyDocPath);
        f.mkdirs();
        tempName = "merge_" + System.currentTimeMillis();
        tempDate = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date());
        tempPages = d1.getPages();
        tempArchived = d1.isArchived() ? "true" : "false";
        Document newDoc = new Document(copyDocPath, tempName, tempDate, d1.getPages(), null, tempArchived);
        for (File toCopy : doc1File.listFiles()) {
            copyFile(doc1Path, copyDocPath, toCopy.getName());
        }
        //adds d2 to end of d1 copy
        int page = (doc1File.listFiles().length - 1) / 2;
        for (int i = 1; i < docList.size(); i++) {
            Document d = docList.get(i);
            String path = d.getPath();
            File file = new File(path);
            if (!file.exists()) continue;
            File[] files = file.listFiles();
            if (files == null || files.length == 0) continue;
            for (int j = 0; j < (files.length - 1) / 2; j++) {
                //rename
                renameFile(path, j + ".jpg", page + ".jpg");
                renameFile(path, j + "_modded.jpg", page + "_modded.jpg");
                //copy
                copyFile(path, copyDocPath, page + ".jpg");
                copyFile(path, copyDocPath, page + "_modded.jpg");
                //rename back
                renameFile(path, page + ".jpg", j + ".jpg");
                renameFile(path, page + "_modded.jpg", j + "_modded.jpg");
                page++;
            }
        }
        tempPages = page + (page == 1 ? " page" : " pages");
        writeAll(newDoc);
        newDoc.setPages(tempPages);
        return newDoc;
    }

    public static Bitmap getBitmap(String pathToFile) {
        return getBitmap(pathToFile, BITMAP_DEFAULT_SCALE);
    }

    public static Bitmap getBitmap(String pathToFile, int scale) {
        File f = new File(pathToFile);
        if (!f.exists()) {
            return null;
        }
        if (!f.getName().contains(".jpg")) {
            return null;
        }

        int orientation = -1;
        Matrix m = new Matrix();
        try {
            ExifInterface exif = new ExifInterface(f.getPath());
            orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
            if(orientation == ExifInterface.ORIENTATION_ROTATE_90) m.postRotate(90);
            else if(orientation == ExifInterface.ORIENTATION_ROTATE_180) m.postRotate(180);
            else if(orientation == ExifInterface.ORIENTATION_ROTATE_270) m.postRotate(270);
        } catch (IOException e) {
            e.printStackTrace();
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), options);
        BitmapFactory.Options loadOptions = new BitmapFactory.Options();
        loadOptions.inSampleSize = scale;
        Bitmap loaded = BitmapFactory.decodeFile(f.getPath(), loadOptions);
        if(orientation != -1) {
            loaded = Bitmap.createBitmap(loaded, 0, 0, loaded.getWidth(), loaded.getHeight(), m, true);
        }

        return loaded;
    }

}
