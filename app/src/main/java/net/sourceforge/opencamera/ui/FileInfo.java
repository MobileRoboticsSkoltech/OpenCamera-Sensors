package net.sourceforge.opencamera.ui;

public class FileInfo {
    private final String mName;
    private final Long mSize;

    public FileInfo(Long size, String name) {
        mName = name;
        mSize = size;
    }

    public Long getSize() {
        return mSize;
    }

    public String getName() {
        return mName;
    }
}
