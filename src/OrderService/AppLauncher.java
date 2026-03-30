package OrderService;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

import quickfix.DefaultMessageFactory;
import quickfix.MemoryStoreFactory;
import quickfix.ScreenLogFactory;
import quickfix.SessionSettings;
import quickfix.SocketAcceptor;

public class AppLauncher {

    public static void main(String[] args) {
        AtomicReference<SocketAcceptor> acceptorRef = new AtomicReference<>();
        AtomicReference<OrderBroadcaster> serverRef = new AtomicReference<>();
        AtomicReference<Thread> persisterThreadRef = new AtomicReference<>();

        Thread shutdownHook = new Thread(() -> stopAll(acceptorRef.get(), serverRef.get(), persisterThreadRef.get()),
                "order-service-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdownHook);

        try {
            System.out.println("Order Service starting...");
            System.out.println("user.dir=" + System.getProperty("user.dir"));
            JdbcOrderStore.verifyConnection();

            BlockingQueue<Order> dbQueue = new LinkedBlockingQueue<>();

            OrderPersister persister = new OrderPersister(dbQueue);
            Thread persisterThread = new Thread(persister, "OrderPersister");
            persisterThreadRef.set(persisterThread);
            persisterThread.start();

            int wsPort = Integer.parseInt(System.getProperty("order.ws.port", "9080"));
            OrderBroadcaster server = new OrderBroadcaster(wsPort);
            serverRef.set(server);
            server.start();
            System.out.println("WebSocket server listening on port " + wsPort + " (override with -Dorder.ws.port=...).");

            OrderApplication application = new OrderApplication(server, dbQueue);
            application.onStart();

            SessionSettings settings = new SessionSettings("order-service.cfg");
            MemoryStoreFactory storeFactory = new MemoryStoreFactory();
            ScreenLogFactory logFactory = new ScreenLogFactory(settings);
            DefaultMessageFactory messageFactory = new DefaultMessageFactory();

            SocketAcceptor acceptor = new SocketAcceptor(application, storeFactory, settings, logFactory, messageFactory);
            acceptorRef.set(acceptor);
            acceptor.start();

            System.out.println("FIX engine started. Listening on port 9876...");
            System.out.println("FIX message store: in-memory (new MsgSeqNum state every JVM start; not ./store).");
            System.out.println();
            System.out.println("Stop: press Enter here, or type quit + Enter, or use Eclipse Terminate / Ctrl+C in a real terminal.");

            BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            String line;
            while ((line = in.readLine()) != null) {
                String t = line.trim();
                if (t.isEmpty() || t.equalsIgnoreCase("quit") || t.equalsIgnoreCase("exit") || t.equalsIgnoreCase("q")) {
                    break;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException ignored) {
            }
            stopAll(acceptorRef.get(), serverRef.get(), persisterThreadRef.get());
            System.out.println("Order Service stopped.");
        }
    }

    private static void stopAll(SocketAcceptor acceptor, OrderBroadcaster server, Thread persisterThread) {
        if (acceptor != null) {
            try {
                acceptor.stop();
            } catch (Exception e) {
                System.err.println("[AppLauncher] FIX acceptor stop: " + e.getMessage());
            }
        }
        if (server != null) {
            try {
                server.stop(2000);
            } catch (Exception e) {
                System.err.println("[AppLauncher] WebSocket stop: " + e.getMessage());
            }
        }
        if (persisterThread != null && persisterThread.isAlive()) {
            persisterThread.interrupt();
            try {
                persisterThread.join(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
