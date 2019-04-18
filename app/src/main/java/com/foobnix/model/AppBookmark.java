package com.foobnix.model;

public class AppBookmark  implements MyPath.RelativePath {
    public String path;
    public String text;

    public float p;
    public long t;

    public AppBookmark() {

    }

    public AppBookmark(String path, String text, float percent) {
        super();
        this.path = MyPath.toRelative(path);
        this.text = text;
        this.p = percent;
        t = System.currentTimeMillis();
    }

    public int getPage(int pages) {
        return Math.round(p * pages);
    }

    public String getText() {
        return text;
    }

    public String getPath() {
        return MyPath.toAbsolute(path);
    }

    public void setPath(String path) {
        this.path = MyPath.toRelative(path);
    }

    public float getPercent() {
        return p;
    }

    public float getTime() {
        return t;
    }

    @Override
    public int hashCode() {
        return (path + text + p).hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        AppBookmark a = (AppBookmark) obj;
        return a.t == t;
    }


}
