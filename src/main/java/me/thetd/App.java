package me.thetd;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;

public class App {

    private static ExecutorService executorService;

    private static volatile int processed = 0;

    public static void main(String[] args) throws SQLException, IOException {
        OptionSpec<String> specDBUrl, specDBUser, specDBPassword, specTablePrefix, specWorld, specMapId;
        OptionSpec<Integer> specZoom, specThreads;

        OptionParser optionParser = new OptionParser();

        specDBUrl = optionParser.accepts("db-url", "like jdbc:mysql://localhost/mc").withRequiredArg().ofType(String.class);
        specDBUser = optionParser.accepts("db-user").withRequiredArg().ofType(String.class);
        specDBPassword = optionParser.accepts("db-password").withRequiredArg().ofType(String.class);
        specTablePrefix = optionParser.accepts("table-prefix", "such as \"dynmap_build_\"").withRequiredArg().ofType(String.class);
        specWorld = optionParser.accepts("world", "world name").withRequiredArg().ofType(String.class);
        specMapId = optionParser.accepts("map-id", "usually was \"flat\"").withRequiredArg().ofType(String.class);
        specZoom = optionParser.accepts("zoom", "zoom, should be integer, starting from 0").withRequiredArg().ofType(Integer.class);
        specThreads = optionParser.accepts("threads", "working threads, should be integer, default to 10, must > 0").withRequiredArg().ofType(Integer.class).defaultsTo(10);

        OptionSet optionSet;
        try {
            optionSet = optionParser.parse(args);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.exit(-1);
            return;
        }

        if (!optionSet.has(specDBUrl) || !optionSet.has(specDBUser)
                || !optionSet.has(specDBPassword) || !optionSet.has(specTablePrefix)
                || !optionSet.has(specWorld) || !optionSet.has(specMapId)
                || !optionSet.has(specZoom)) {
            optionParser.printHelpOn(System.err);
            System.exit(-1);
            return;
        }

        String dbUrl = specDBUrl.value(optionSet);
        String dbUser = specDBUser.value(optionSet);
        String dbPassword = specDBPassword.value(optionSet);
        String tablePrefix = specTablePrefix.value(optionSet);
        String world = specWorld.value(optionSet);
        String mapId = specMapId.value(optionSet);
        int zoom = specZoom.value(optionSet);
        int threads = specThreads.value(optionSet);
        if (threads <= 0) {
            System.err.println("threads must > 0");
            return;
        }

        executorService = Executors.newFixedThreadPool(threads);

        long start = System.currentTimeMillis();

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(dbUrl);
        hikariConfig.setUsername(dbUser);
        hikariConfig.setPassword(dbPassword);

        HikariDataSource dataSource = new HikariDataSource(hikariConfig);

        int intMapId;
        int leftTopX;
        int leftTopY;
        int width;
        int height;

        try (Connection conn = dataSource.getConnection()) {
            ResultSet r = conn.createStatement().executeQuery("SELECT ID FROM `" + tablePrefix + "Maps` WHERE `WorldId`=\"" + world + "\" AND MapID=\"" + mapId + "\"");
            if (!r.next()) {
                System.err.println("map not found");
                return;
            }

            intMapId = r.getInt(1);

            r = conn.createStatement().executeQuery("SELECT min(x) FROM `" + tablePrefix + "Tiles` WHERE MapID=" + intMapId + " AND zoom=" + zoom);
            if (!r.next()) {
                System.err.println("specified map does not exists");
            }

            leftTopX = r.getInt(1);

            r = conn.createStatement().executeQuery("SELECT min(y) FROM `" + tablePrefix + "Tiles` WHERE MapID=" + intMapId + " AND zoom=" + zoom);
            r.next();
            leftTopY = r.getInt(1);

            r = conn.createStatement().executeQuery("SELECT max(x) FROM `" + tablePrefix + "Tiles` WHERE MapID=" + intMapId + " AND zoom=" + zoom);
            r.next();
            int rightBottomX = r.getInt(1);

            width = rightBottomX - leftTopX;

            r = conn.createStatement().executeQuery("SELECT max(y) FROM `" + tablePrefix + "Tiles` WHERE MapID=" + intMapId + " AND zoom=" + zoom);
            r.next();
            int rightBottomY = r.getInt(1);
            height = rightBottomY - leftTopY;
        }

        System.out.println("mapId = " + intMapId);
        System.out.println("zoom = " + zoom);
        System.out.println("leftTopX = " + leftTopX);
        System.out.println("leftTopY = " + leftTopY);
        System.out.println("width = " + width);
        System.out.println("height = " + height);

        BlockingDeque<MapTile> queue = new LinkedBlockingDeque<>();
        int step = (int) Math.pow(2, zoom);

        for (int x = 0; x < width / step; x++) {
            for (int y = 0; y < height / step; y++) {
                MapTile tile = new MapTile(leftTopX + (x * step), leftTopY + (y * step), x, y);
                queue.add(tile);
            }
        }

        int total = queue.size();

        final BufferedImage finalImg = new BufferedImage((width / step) * 128, (height / step) * 128, BufferedImage.TYPE_INT_RGB);

        for (int i = 0; i < threads; i++) {
            executorService.execute(new FetchTask(dataSource, tablePrefix, intMapId, zoom, queue) {
                @Override
                public void onFinish(MapTile tile, BufferedImage image) {
                    synchronized (finalImg) {
                        finalImg.getGraphics().drawImage(image, tile.getImgX() * 128, finalImg.getHeight() - (tile.getImgY() * 128), null);
                        processed++;
                        finalImg.notify();
                    }
                }
            });
        }

        boolean finished = false;
        while (!finished) {
            synchronized (finalImg) {
                try {
                    finalImg.wait();
                } catch (InterruptedException e) {
                    return;
                }
                System.out.println(String.format("progress: %.2f%%", (processed * 100 / (double) total)));

                if (processed == total) finished = true;
            }
        }

        File container = new File(world + "_" + mapId + "_" + zoom + ".jpg");
        try {
            finalImg.flush();
            ImageIO.write(finalImg, "jpg", container);
        } catch (IOException e) {
            System.err.println("error writing result");
        }

        long end = System.currentTimeMillis();

        System.out.println(String.format("finished in %.2f seconds", (end - start) / (double) 1000));
        System.out.println("output to " + container.getName());

        executorService.shutdown();
    }
}
