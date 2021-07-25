package com.chijo.scanner;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class FileHelper {

    private static String tempName = "";
    private static String tempDate = "";
    private static String tempPages = "";
    private static String tempArchived = "";

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

}
