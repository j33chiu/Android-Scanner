package com.chijo.scanner;

import android.graphics.Bitmap;

class Document {
    private String path;
    private String documentName;
    private String lastUpdatedDate;
    private String pages;
    private String isArchived = "false";

    public Document(String path, String documentName, String lastUpdatedDate, String pages, Bitmap imagePreview, String isArchived) {
        this.path = path;
        this.documentName = documentName;
        this.lastUpdatedDate = lastUpdatedDate;
        this.pages = pages;
        this.isArchived = isArchived;
    }

    public String getPath() {
        return path;
    }

    String getDocumentName() {
        return documentName;
    }

    void setDocumentName(String name) {
        documentName = name;
    }

    String getLastUpdatedDate() {
        return lastUpdatedDate;
    }

    String getPages() {
        return pages;
    }

    boolean isArchived() {
        if(isArchived == null) return false;
        return isArchived.equals("true");
    }

    boolean search(String text) {
        return documentName.toLowerCase().contains(text.toLowerCase());
    }
}
