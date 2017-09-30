package me.thetd;

public class MapTile {
    private final int x;
    private final int y;
    private final int imgX;
    private final int imgY;

    public MapTile(int x, int y, int imgX, int imgY) {
        this.x = x;
        this.y = y;
        this.imgX = imgX;
        this.imgY = imgY;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getImgX() {
        return imgX;
    }

    public int getImgY() {
        return imgY;
    }

    @Override
    public String toString() {
        return String.format("MapTile(x=%d, y=%d)", x, y);
    }
}
