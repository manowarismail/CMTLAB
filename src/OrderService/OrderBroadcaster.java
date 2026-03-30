package OrderService;

import org.java_websocket.server.WebSocketServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import java.net.InetSocketAddress;
import com.google.gson.Gson;

import quickfix.FieldNotFound;
import quickfix.Message;
import quickfix.Session;
import quickfix.SessionID;
import quickfix.field.AvgPx;
import quickfix.field.ClOrdID;
import quickfix.field.CumQty;
import quickfix.field.ExecID;
import quickfix.field.ExecTransType;
import quickfix.field.LeavesQty;
import quickfix.field.MsgSeqNum;
import quickfix.field.MsgType;
import quickfix.field.OrdStatus;
import quickfix.field.OrderID;
import quickfix.field.RefMsgType;
import quickfix.field.RefSeqNum;
import quickfix.field.RefTagID;
import quickfix.field.SessionRejectReason;
import quickfix.field.Side;
import quickfix.field.Symbol;
import quickfix.field.Text;
import quickfix.fix42.ExecutionReport;
import quickfix.fix42.Reject;

public class OrderBroadcaster extends WebSocketServer {

    private final Gson gson = new Gson();

    public OrderBroadcaster(int port) {
        super(new InetSocketAddress(port));
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        System.out.println("UI Connected: " + conn.getRemoteSocketAddress());
        conn.send("{\"test\":\"HELLO\"}");
    }
  
    @Override
    public void onMessage(WebSocket conn, String message) {
        System.out.println("Message received from UI: " + message);
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        System.out.println("Connection closed: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.out.println("WebSocket error:");
        ex.printStackTrace();
    }

    @Override
    public void onStart() {
        System.out.println("WebSocket server started successfully.");
    }

    public void acceptOrder(Order order, SessionID sessionId) {
        sendFixAck(order, sessionId);
        broadcastOrder(order);
    }

    public void sendSessionReject(Message refAppMessage, SessionID sessionId, String reasonText) {
        try {
            int refSeq = refAppMessage.getHeader().getInt(MsgSeqNum.FIELD);
            String refMsgType = refAppMessage.getHeader().getString(MsgType.FIELD);
            Reject reject = new Reject();
            reject.set(new RefSeqNum(refSeq));
            reject.set(new RefMsgType(refMsgType));
            reject.set(new RefTagID(Symbol.FIELD));
            reject.set(new SessionRejectReason(SessionRejectReason.VALUE_IS_INCORRECT));
            reject.set(new Text(reasonText));
            Session.sendToTarget(reject, sessionId);
            System.out.println("[OrderBroadcaster] Session Reject (35=3) refSeq=" + refSeq + " refMsgType=" + refMsgType
                    + " text=" + reasonText);
        } catch (FieldNotFound e) {
            System.err.println("[OrderBroadcaster] Cannot build Session Reject (missing header field): " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("[OrderBroadcaster] Failed to send Session Reject");
            e.printStackTrace();
        }
    }

    private void sendFixAck(Order order, SessionID sessionId) {
        try {
            ExecutionReport report = new ExecutionReport();
            report.set(new OrderID(order.getOrderId()));
            report.set(new ExecID(String.valueOf(System.nanoTime())));
            report.set(new ExecTransType(ExecTransType.NEW));
            report.set(new OrdStatus(OrdStatus.NEW));
            report.set(new ClOrdID(order.getClOrdID()));
            report.set(new Symbol(order.getSymbol()));
            report.set(new Side(order.getSide()));
            report.set(new LeavesQty(order.getQuantity()));
            report.set(new CumQty(0));
            report.set(new AvgPx(order.getPrice()));
            Session.sendToTarget(report, sessionId);
        } catch (Exception e) {
            System.err.println("[OrderBroadcaster] Failed to send ExecutionReport for " + order.getClOrdID());
            e.printStackTrace();
        }
    }

    public void broadcastOrder(Order order) {
        int connections = getConnections().size();
        if (connections == 0) {
            return;
        }
        String json = gson.toJson(order);
        System.out.println("[UI] broadcast " + order.getClOrdID() + " to " + connections + " client(s)");
        broadcast(json);
    }
}