package me.thetd;

import javax.imageio.ImageIO;
import javax.sql.DataSource;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class FetchTask implements Runnable {
    private final static BufferedImage EMPTY_IMAGE = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
    private final DataSource dataSource;
    private final String tablePrefix;
    private final int mapId;
    private final int zoom;
    private final BlockingDeque<MapTile> queue;

    public FetchTask(DataSource dataSource, String tablePrefix, int mapId, int zoom, BlockingDeque<MapTile> queue) {
        this.dataSource = dataSource;
        this.tablePrefix = tablePrefix;
        this.mapId = mapId;
        this.zoom = zoom;
        this.queue = queue;
    }

    @Override
    public void run() {
        MapTile tile;
        while ((tile = getNextEntry()) != null) {
            try (Connection conn = dataSource.getConnection()) {
                ResultSet r = conn.createStatement().executeQuery("SELECT Image FROM `" + tablePrefix + "Tiles` WHERE MapID=" + mapId + " AND x=" + tile.getX() + " AND y=" + tile.getY() + " AND zoom=" + zoom);
                if (r.next()) {
                    BufferedImage image = ImageIO.read(r.getBlob(1).getBinaryStream());
                    onFinish(tile, image);
                } else {
                    onFinish(tile, EMPTY_IMAGE);
                }
            } catch (Exception e) {
                Logger.getLogger(FetchTask.class.getName()).log(Level.SEVERE, String.format("task %s failed", tile), e);
                queue.addFirst(tile);
            }
        }
    }

    private MapTile getNextEntry() {
        try {
            return queue.poll(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            return null;
        }
    }

    public abstract void onFinish(MapTile tile, BufferedImage image);
}
