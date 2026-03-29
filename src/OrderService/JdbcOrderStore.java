package OrderService;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

public final class JdbcOrderStore {

    private static final String DRIVER_CLASS = "com.mysql.cj.jdbc.Driver";
    private static final String DRIVER_JAR_NAME = "mysql-connector-j-8.3.0.jar";

    private static final String JDBC_URL = "jdbc:mysql://localhost:3306/trading_system"
            + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    private static final String USER = "trader";
    private static final String PASS = "TraderPass123";

    private static volatile boolean driverLoaded;
    private static volatile Driver mysqlDriver;

    static {
        System.out.println("[JdbcOrderStore] MySQL access via Driver.connect (class renamed from DatabaseManager to avoid stale Eclipse .class files).");
        loadDriverOnce();
    }

    private static Path findDriverJar() {
        Path wd = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        Path[] quick = new Path[] {
                wd.resolve("lib").resolve(DRIVER_JAR_NAME),
                wd.resolve("OrderService").resolve("lib").resolve(DRIVER_JAR_NAME),
                wd.resolve("eclipse-workspace").resolve("OrderService").resolve("lib").resolve(DRIVER_JAR_NAME),
        };
        for (Path candidate : quick) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        Path fallback = quick[0];
        try {
            URL loc = JdbcOrderStore.class.getProtectionDomain().getCodeSource().getLocation();
            if (loc != null && "file".equals(loc.getProtocol())) {
                Path code = Paths.get(loc.toURI()).toAbsolutePath().normalize();
                Path p = code;
                for (int depth = 0; depth < 10 && p != null; depth++) {
                    Path candidate = p.resolve("lib").resolve(DRIVER_JAR_NAME);
                    if (Files.isRegularFile(candidate)) {
                        return candidate;
                    }
                    p = p.getParent();
                }
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private static Driver instantiateDriver(ClassLoader loader)
            throws ClassNotFoundException, ReflectiveOperationException {
        Class<?> driverClass = Class.forName(DRIVER_CLASS, true, loader);
        return (Driver) driverClass.getDeclaredConstructor().newInstance();
    }

    private static void loadDriverOnce() {
        if (driverLoaded) {
            return;
        }
        synchronized (JdbcOrderStore.class) {
            if (driverLoaded) {
                return;
            }
            ClassLoader appCl = JdbcOrderStore.class.getClassLoader();
            try {
                mysqlDriver = instantiateDriver(appCl);
                driverLoaded = true;
                System.out.println("[JdbcOrderStore] MySQL driver from application classpath.");
                return;
            } catch (ClassNotFoundException e) {
                // fall through
            } catch (ReflectiveOperationException e) {
                System.err.println("[JdbcOrderStore] Driver on classpath but failed to instantiate: " + e.getMessage());
            }

            Path jar = findDriverJar();
            if (!Files.isRegularFile(jar)) {
                System.err.println("[JdbcOrderStore] Missing " + DRIVER_JAR_NAME + ". Expected at: " + jar.toAbsolutePath());
                return;
            }
            try {
                URL jarUrl = jar.toUri().toURL();
                ClassLoader parent = appCl != null ? appCl : ClassLoader.getSystemClassLoader();
                @SuppressWarnings("resource")
                URLClassLoader ucl = new URLClassLoader(new URL[] { jarUrl }, parent);
                mysqlDriver = instantiateDriver(ucl);
                driverLoaded = true;
                System.out.println("[JdbcOrderStore] MySQL driver from file: " + jar.toAbsolutePath());
            } catch (Exception e) {
                System.err.println("[JdbcOrderStore] Failed to load driver from " + jar.toAbsolutePath());
                e.printStackTrace();
            }
        }
    }

    private static Connection openConnection() throws SQLException {
        loadDriverOnce();
        if (mysqlDriver == null) {
            throw new SQLException("MySQL JDBC driver not available. Add lib/" + DRIVER_JAR_NAME + " and refresh the project.");
        }
        Properties p = new Properties();
        p.setProperty("user", USER);
        p.setProperty("password", PASS);
        Connection conn = mysqlDriver.connect(JDBC_URL, p);
        if (conn == null) {
            throw new SQLException("MySQL driver rejected URL.");
        }
        return conn;
    }

    public static boolean verifyConnection() {
        try (Connection conn = openConnection();
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT COUNT(*) FROM information_schema.tables "
                                + "WHERE table_schema = 'trading_system' AND table_name = 'orders'")) {
            if (rs.next() && rs.getInt(1) == 1) {
                System.out.println("[JdbcOrderStore] OK: trading_system.orders exists.");
                return true;
            }
            System.err.println("[JdbcOrderStore] Table orders missing. Run sql/init_orders_table.sql");
            return false;
        } catch (SQLException e) {
            System.err.println("[JdbcOrderStore] Cannot connect or query MySQL.");
            logSql(e);
            return false;
        }
    }

    public static void insertOrder(Order order) {
        String sql = "INSERT INTO orders (order_id, cl_ord_id, symbol, side, price, quantity, status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = openConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            conn.setAutoCommit(true);
            pstmt.setString(1, order.getOrderId());
            pstmt.setString(2, order.getClOrdID());
            pstmt.setString(3, order.getSymbol());
            pstmt.setString(4, String.valueOf(order.getSide()));
            pstmt.setDouble(5, order.getPrice());
            pstmt.setDouble(6, order.getQuantity());
            pstmt.setString(7, order.getStatus());
            int rows = pstmt.executeUpdate();
            if (rows > 0) {
                System.out.println("[JdbcOrderStore] Order persisted: " + order.getClOrdID());
            } else {
                System.err.println("[JdbcOrderStore] Insert returned 0 rows: " + order.getClOrdID());
            }
        } catch (SQLException e) {
            System.err.println("[JdbcOrderStore] SQLException for order: " + order.getClOrdID());
            logSql(e);
        }
    }

    private static void logSql(SQLException e) {
        for (SQLException x = e; x != null; x = x.getNextException()) {
            System.err.println("[JdbcOrderStore] SQLState=" + x.getSQLState() + " code=" + x.getErrorCode() + " msg=" + x.getMessage());
            x.printStackTrace();
        }
    }
}
