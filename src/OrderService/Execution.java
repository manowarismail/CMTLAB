package OrderService;

import java.util.UUID;

public final class Execution {

    private final String executionId;
    private final String orderId;
    private final String execId;
    private final String incomingClOrdID;
    private final String restingClOrdID;
    private final String symbol;
    private final char side;
    private final double quantity;
    private final double price;

    public Execution(Order incoming, Order resting, double quantity, double price) {
        this.executionId = UUID.randomUUID().toString();
        this.orderId = incoming.getOrderId();
        this.execId = String.valueOf(System.nanoTime());
        this.incomingClOrdID = incoming.getClOrdID();
        this.restingClOrdID = resting.getClOrdID();
        this.symbol = incoming.getSymbol();
        this.side = incoming.getSide();
        this.quantity = quantity;
        this.price = price;
    }

    public String getExecutionId() {
        return executionId;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getExecId() {
        return execId;
    }

    public String getIncomingClOrdID() {
        return incomingClOrdID;
    }

    public String getRestingClOrdID() {
        return restingClOrdID;
    }

    public String getSymbol() {
        return symbol;
    }

    public char getSide() {
        return side;
    }

    public double getQuantity() {
        return quantity;
    }

    public double getPrice() {
        return price;
    }
}

