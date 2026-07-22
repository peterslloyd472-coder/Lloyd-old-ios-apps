package ca.litten.ios_obscura_server.frontend;

import ca.litten.ios_obscura_server.Main;
import ca.litten.ios_obscura_server.backend.App;
import ca.litten.ios_obscura_server.backend.AppList;
import ca.litten.ios_obscura_server.parser.CPUarch;
import com.dd.plist.NSArray;
import com.dd.plist.NSDictionary;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.spi.HttpServerProvider;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Server {
    private final HttpServer server;
    private static final HttpServerProvider provider = HttpServerProvider.provider();
    private static final Random rand = new Random();
    private static final byte[] searchIcon;
    private static final byte[] searchIcon7;
    private static final byte[] favicon;
    private static final byte[] mainicon;
    private static final byte[] icon32;
    private static final byte[] icon16;
    private static final byte[] iconMask7;
    private static long lastReload = 0;
    public static boolean allowReload = false;
    private static String serverName = "localhost";
    private static String donateURL = "";
    private static String repeaterPrefix = "";
    private static String headerTag = "";
    private static boolean recentUrlUpdate = false;
    private static int port;
    private static ErrorPageCreator errorPages;
    private static final LinkedList<String> featuredApps = new LinkedList<>();
    private final ThreadPoolExecutor serverExecutor;
    
    static {
        try {
            File file = new File("searchIcon.jpg");
            FileInputStream search = new FileInputStream(file);
            searchIcon = new byte[Math.toIntExact(file.length())];
            search.read(searchIcon);
            search.close();
            file = new File("searchIcon7.jpg");
            FileInputStream search7 = new FileInputStream(file);
            searchIcon7 = new byte[Math.toIntExact(file.length())];
            search7.read(searchIcon7);
            search7.close();
            file = new File("iconMask7.svg");
            FileInputStream mask7 = new FileInputStream(file);
            iconMask7 = new byte[Math.toIntExact(file.length())];
            mask7.read(iconMask7);
            mask7.close();
            file = new File("favicon.ico");
            FileInputStream fav = new FileInputStream(file);
            favicon = new byte[Math.toIntExact(file.length())];
            fav.read(favicon);
            fav.close();
            file = new File("icon.png");
            FileInputStream icon = new FileInputStream(file);
            mainicon = new byte[Math.toIntExact(file.length())];
            icon.read(mainicon);
            icon.close();
            file = new File("icon16.png");
            FileInputStream icon16f = new FileInputStream(file);
            icon16 = new byte[Math.toIntExact(file.length())];
            icon16f.read(icon16);
            icon16f.close();
            file = new File("icon32.png");
            FileInputStream icon32f = new FileInputStream(file);
            icon32 = new byte[Math.toIntExact(file.length())];
            icon32f.read(icon32);
            icon32f.close();
            file = new File("config.json");
            FileReader reader = new FileReader(file);
            StringBuilder out = new StringBuilder();
            char[] buf = new char[4096];
            int read;
            while (reader.ready()) {
                read = reader.read(buf);
                for (int i = 0; i < read; i++)
                    out.append(buf[i]);
            }
            JSONObject object = new JSONObject(out.toString());
            serverName = object.getString("host");
            donateURL = object.getString("donate_url");
            headerTag = object.getString("header_tags");
            repeaterPrefix = object.getString("repeater_url_prefix");
            try {
                recentUrlUpdate = object.getBoolean("recent_url_change");
            } catch (Exception e) {
                recentUrlUpdate = false;
            }
            errorPages = new ErrorPageCreator(headerTag);
            port = object.getInt("port");
            try {
                for (Object o : object.getJSONArray("featured")) {
                    featuredApps.addLast(o.toString());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final String iOS7mask = "-webkit-mask-image:url(\"/getIconMask7\");-webkit-mask-size:cover;mask-image:url(\"/getIconMask7\");mask-size:cover;";

    public Server() throws IOException {
        lastReload = System.currentTimeMillis();
        server = provider.createHttpServer(new InetSocketAddress(port), -1);
        serverExecutor = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors() * 4,
                Runtime.getRuntime().availableProcessors() * 1024,
                120, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        server.setExecutor(serverExecutor);
        server.createContext("/").setHandler(exchange -> {
            Headers incomingHeaders = exchange.getRequestHeaders();
            Headers outgoingHeaders = exchange.getResponseHeaders();
            outgoingHeaders.set("Content-Type", "text/html; charset=utf-8");
            String userAgent = incomingHeaders.get("user-agent").get(0);
            boolean iOS_connection = userAgent.contains("iPhone OS") || userAgent.contains("iPad");
            String iOS_ver = "99999999";
            if (iOS_connection) {
                String[] split1 = userAgent.split("like Mac OS X");
                String[] split2 = split1[0].split(" ");
                String[] iOS_ver_split = split2[split2.length - 1].split("_");
                int end_index = iOS_ver_split.length - 1;
                while (end_index > 0 && iOS_ver_split[end_index].equals("0")) end_index--;
                iOS_ver = "";
                for (int index = 0; index <= end_index; index++)
                    iOS_ver = iOS_ver + iOS_ver_split[index] + ".";
                if (iOS_ver.indexOf('.') == iOS_ver.lastIndexOf('.')) iOS_ver = iOS_ver + "0";
                else iOS_ver = iOS_ver.substring(0, iOS_ver.length() - 1);
            }
            String exchangeURI = exchange.getRequestURI().toString();
            if (!(exchangeURI.equals("/") || exchangeURI.isEmpty() || exchangeURI.toLowerCase().equals("/debug")
                    || exchangeURI.toLowerCase().equals("/debug/"))) {
                byte[] bytes = errorPages.general404.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(404, bytes.length);
                if (!exchange.getRequestMethod().equalsIgnoreCase("HEAD")) exchange.getResponseBody().write(bytes);
                exchange.close();
                return;
            }
            boolean debugMode = exchangeURI.toLowerCase().contains("debug");
            StringBuilder out = new StringBuilder();
            out.append(Templates.generateBasicHeader("Legacy iOS App Finder", headerTag))
                    .append("<body class=\"pinstripe\"><panel><fieldset><div><div><center><strong>Legacy iOS App Finder Homepage</strong></center></div></div>");
            if (debugMode) out.append("<div><div>Debug Mode</div></div>");
            out.append("<div><div><form action=\"");
            if (debugMode) out.append("/debug");
            out.append("/searchPost\"><input type=\"text\" name=\"search\" value=\"\" style=\"-webkit-appearance:none;border-bottom:1px solid #999\" placeholder=\"Search\"><button style=\"float:right;background:none\" type=\"submit\"><img class=\"search\" src=\"/searchIcon\"></button></form></div></div></fieldset>");
            if (recentUrlUpdate)
                out.append("<fieldset style=\"background-color:#fcc\"><a href=\"http://")
                        .append(serverName).append(exchangeURI)
                        .append("\"><div><div>The location of this website has changed! The new link is ")
                        .append(serverName).append("</div></div></a></fieldset>");
            App app;
            if (!featuredApps.isEmpty()) {
                out.append("<label>Featured Apps</label><fieldset class=\"iconList\">");
                boolean foundApps = false;
                for (String bundleID : featuredApps) {
                    app = AppList.getAppByBundleID(bundleID);
                    if (app == null) continue; // Version check removed to support all iOS devices
                    foundApps = true;
                    if (debugMode)
                        out.append("<a href=\"/debug/getAppVersions/").append(app.getBundleID())
                                .append("\"><div><div><img loading=\"lazy\" class=\"appIcon\" src=\"/getAppIcon/")
                                .append(app.getBundleID()).append("\" onerror=\"this.onerror=null;this.src='/getProxiedAppIcon/")
                                .append(app.getBundleID()).append("'\"><center style=\"line-height: 11px\"><br>").append(cutStringTo(app.getName(), 15))
                                .append("<br><small style=\"font-size:x-small\">");
                }
            }
            exchange.sendResponseHeaders(200, out.toString().getBytes(StandardCharsets.UTF_8).length);
            exchange.getResponseBody().write(out.toString().getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });
        
        server.createContext("/searchIcon").setHandler(exchange -> {
