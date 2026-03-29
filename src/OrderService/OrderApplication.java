package OrderService;

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

    public OrderApplication(OrderBroadcaster server, BlockingQueue<Order> dbQueue) {
        this.server = server;
        this.dbQueue = dbQueue;
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
    public void onLogon(SessionID sessionId) {}

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
            String clOrdID = message.getString(11);
            String symbol = message.getString(55);
            char side = message.getChar(54);
            double price = message.isSetField(44) ? message.getDouble(44) : 0.0;
            double quantity = message.getDouble(38);
            handleNewOrderSingle(clOrdID, symbol, side, price, quantity, sessionId);
        } catch (FieldNotFound e) {
            System.err.println("[OrderApplication] NewOrderSingle missing field tag: " + e.field);
            throw e;
        }
    }

    public void onMessage(NewOrderSingle orderMsg, SessionID sessionId) throws FieldNotFound {
        String clOrdID = orderMsg.getClOrdID().getValue();
        String symbol = orderMsg.getSymbol().getValue();
        char side = orderMsg.getSide().getValue();
        double price = orderMsg.getPrice().getValue();
        double quantity = orderMsg.getOrderQty().getValue();
        handleNewOrderSingle(clOrdID, symbol, side, price, quantity, sessionId);
    }

    private void handleNewOrderSingle(String clOrdID, String symbol, char side, double price, double quantity,
            SessionID sessionId) {
        try {
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
