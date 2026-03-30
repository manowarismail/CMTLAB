package OrderService;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

import quickfix.Application;
import quickfix.FieldNotFound;
import quickfix.IncorrectTagValue;
import quickfix.Message;
import quickfix.SessionID;
import quickfix.MessageCracker;
import quickfix.UnsupportedMessageType;
import quickfix.fix42.NewOrderSingle;

public class OrderApplication extends MessageCracker implements Application {

    private final OrderBroadcaster server;
    private final BlockingQueue<Order> dbQueue;
    private final Map<String, Security> validSecurities = new HashMap<>();

    public OrderApplication(OrderBroadcaster server, BlockingQueue<Order> dbQueue) {
        this.server = server;
        this.dbQueue = dbQueue;
    }

    public void onStart() {
        reloadSecurityMaster();
    }

    private void reloadSecurityMaster() {
        validSecurities.clear();
        try {
            validSecurities.putAll(JdbcOrderStore.loadSecurityMaster());
            System.out.println("[OrderApplication] security_master in memory: " + validSecurities.size() + " row(s).");
        } catch (SQLException e) {
            System.err.println("[OrderApplication] Failed to load security_master; unknown-symbol rejects until fixed.");
            JdbcOrderStore.logSql(e);
        }
    }

    @Override
    public void fromAdmin(Message message, SessionID sessionId) {}

    @Override
    public void fromApp(Message message, SessionID sessionId) {
        try {
            String msgType = message.getHeader().getString(35);
            if (!"0".equals(msgType) && !"1".equals(msgType) && !"D".equals(msgType)) {
                System.out.println("[OrderApplication] fromApp MsgType=" + msgType
                        + " runtimeClass=" + message.getClass().getSimpleName());
            }
            crack(message, sessionId);
        } catch (UnsupportedMessageType e) {
            System.err.println("[OrderApplication] Unsupported app message type (class="
                    + message.getClass().getName() + ")");
        } catch (FieldNotFound e) {
            System.err.println("[OrderApplication] fromApp missing MsgType");
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onCreate(SessionID sessionId) {}

    @Override
    public void onLogout(SessionID sessionId) {}

    @Override
    public void onLogon(SessionID sessionId) {
        reloadSecurityMaster();
    }

    @Override
    public void toAdmin(Message message, SessionID sessionId) {}

    @Override
    public void toApp(Message message, SessionID sessionId) {}

    @Override
    protected void onMessage(Message message, SessionID sessionId)
            throws FieldNotFound, UnsupportedMessageType, IncorrectTagValue {
        String msgType = message.getHeader().getString(35);
        if (!"D".equals(msgType)) {
            throw new UnsupportedMessageType();
        }
        try {
            handleNewOrderSingle(message, sessionId);
        } catch (FieldNotFound e) {
            System.err.println("[OrderApplication] NewOrderSingle missing field tag: " + e.field);
            throw e;
        }
    }

    public void onMessage(NewOrderSingle orderMsg, SessionID sessionId) throws FieldNotFound {
        handleNewOrderSingle(orderMsg, sessionId);
    }

    private void handleNewOrderSingle(Message incoming, SessionID sessionId) throws FieldNotFound {
        String clOrdID = incoming.getString(11);
        String symbol = incoming.getString(55);
        char side = incoming.getChar(54);
        double price = incoming.isSetField(44) ? incoming.getDouble(44) : 0.0;
        double quantity = incoming.getDouble(38);
        try {
            String symKey = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
            if (!validSecurities.containsKey(symKey)) {
                System.out.println("[OrderApplication] Reject: symbol key='" + symKey + "' not in map (size="
                        + validSecurities.size() + "). Restart or reconnect FIX after changing security_master.");
                server.sendSessionReject(incoming, sessionId, "Unknown Security");
                return;
            }
            Order order = new Order(clOrdID, symbol, side, price, quantity, "NEW");
            server.acceptOrder(order, sessionId);
            if (!dbQueue.offer(order)) {
                System.err.println("[OrderApplication] dbQueue full, order not queued: " + clOrdID);
            }
        } catch (Exception e) {
            System.err.println("[OrderApplication] Failed to process order message!");
            e.printStackTrace();
        }
    }
}
