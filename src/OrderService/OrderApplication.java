package OrderService;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

import quickfix.Application;
import quickfix.FieldNotFound;
import quickfix.IncorrectTagValue;
import quickfix.Message;
import quickfix.SessionID;
import quickfix.MessageCracker;
import quickfix.UnsupportedMessageType;
import quickfix.Session;
import quickfix.field.ClOrdID;
import quickfix.field.CumQty;
import quickfix.field.ExecID;
import quickfix.field.ExecType;
import quickfix.field.ExecTransType;
import quickfix.field.LastShares;
import quickfix.field.LastPx;
import quickfix.field.LeavesQty;
import quickfix.field.OrdStatus;
import quickfix.field.OrderQty;
import quickfix.field.OrderID;
import quickfix.field.Side;
import quickfix.field.Symbol;
import quickfix.fix42.NewOrderSingle;
import quickfix.fix42.ExecutionReport;

public class OrderApplication extends MessageCracker implements Application {

    private final OrderBroadcaster server;
    private final BlockingQueue<Object> dbQueue;
    private final Map<String, Security> validSecurities = new HashMap<>();
    private final Map<String, OrderBook> booksBySymbol = new ConcurrentHashMap<>();
    private final ThreadLocal<Long> ingressNanos = new ThreadLocal<>();

    public OrderApplication(OrderBroadcaster server, BlockingQueue<Object> dbQueue) {
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
        long ingressTime = System.nanoTime();
        ingressNanos.set(ingressTime);
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
        } finally {
            ingressNanos.remove();
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
                sendRejectResponse(incoming, sessionId, "Unknown Security", currentIngressTime());
                return;
            }
            Order order = new Order(clOrdID, symbol, side, price, quantity, "NEW");

            OrderBook book = booksBySymbol.computeIfAbsent(symKey, k -> new OrderBook(k));
            List<Execution> trades = book.match(order);
            if (!dbQueue.offer(order)) {
                System.err.println("[OrderApplication] dbQueue full, order not queued: " + clOrdID);
                return;
            }

            if (!trades.isEmpty()) {
                System.out.println("[OrderApplication] Trades generated for " + symKey + ": " + trades.size());
                for (Execution trade : trades) {
                    if (!dbQueue.offer(trade)) {
                        System.err.println("[OrderApplication] dbQueue full, execution not queued: " + trade.getExecId());
                    }
                    server.sendTradeUpdate(trade);
                    sendFillReport(trade, sessionId, currentIngressTime());
                }
            }

            if (trades.isEmpty() || order.getQuantity() > 0) {
                sendAcceptOrder(order, sessionId, currentIngressTime());
            }
        } catch (Exception e) {
            System.err.println("[OrderApplication] Failed to process order message!");
            e.printStackTrace();
        }
    }

    private void sendFillReport(Execution trade, SessionID sessionId, long ingressTime) {
        try {
            ExecutionReport fixTrade = new ExecutionReport();
            fixTrade.set(new OrderID(trade.getOrderId()));
            fixTrade.set(new ExecID(trade.getExecId()));
            fixTrade.set(new ExecTransType(ExecTransType.NEW));
            fixTrade.set(new ExecType(ExecType.TRADE));
            fixTrade.set(new OrdStatus(OrdStatus.FILLED));
            fixTrade.set(new ClOrdID(trade.getIncomingClOrdID()));
            fixTrade.set(new Symbol(trade.getSymbol()));
            fixTrade.set(new Side(trade.getSide()));
            fixTrade.set(new OrderQty(trade.getQuantity()));
            fixTrade.set(new LastPx(trade.getPrice()));
            fixTrade.set(new LastShares(trade.getQuantity()));
            fixTrade.set(new CumQty(trade.getQuantity()));
            fixTrade.set(new LeavesQty(0));
            Session.sendToTarget(fixTrade, sessionId);
            recordLatency(ingressTime);
        } catch (Exception e) {
            System.err.println("[OrderApplication] Failed to send fill report for execution " + trade.getExecId());
            e.printStackTrace();
        }
    }

    private void sendAcceptOrder(Order order, SessionID sessionId, long ingressTime) {
        server.acceptOrder(order, sessionId);
        recordLatency(ingressTime);
    }

    private void sendRejectResponse(Message incoming, SessionID sessionId, String reason, long ingressTime) {
        server.sendSessionReject(incoming, sessionId, reason);
        recordLatency(ingressTime);
    }

    private long currentIngressTime() {
        Long ingress = ingressNanos.get();
        return ingress == null ? System.nanoTime() : ingress.longValue();
    }

    private void recordLatency(long ingressTime) {
        long egressTime = System.nanoTime();
        long latency = egressTime - ingressTime;
        PerformanceMonitor.recordLatency(latency);
    }
}
