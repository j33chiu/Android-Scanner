package com.chijo.scanner;

import android.graphics.Bitmap;

class Document {
    private String path;
    private String documentName;
    private String lastUpdatedDate;
    private String pages;
    private String isArchived = "false";
    private DisplayData uiData;

    public Document(String path, String documentName, String lastUpdatedDate, String pages, Bitmap imagePreview, String isArchived) {
        this.path = path;
        this.documentName = documentName;
        this.lastUpdatedDate = lastUpdatedDate;
        this.pages = pages;
        this.isArchived = isArchived;
        uiData = new DisplayData();
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

    void setPages(String pages) {
        this.pages = pages;
    }

    boolean isArchived() {
        if(isArchived == null) return false;
        return isArchived.equals("true");
    }

    void setIsArchived(String isArchived) {
        this.isArchived = isArchived;
    }

    boolean search(String text) {
        return documentName.toLowerCase().contains(text.toLowerCase());
    }

    boolean getSelected() {
        return uiData.checked;
    }

    void setSelected(boolean checked) {
        uiData.checked = checked;
    }
}
