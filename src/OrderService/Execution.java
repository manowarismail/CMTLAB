package OrderService;

public final class Execution {

    private final String incomingClOrdID;
    private final String restingClOrdID;
    private final String symbol;
    private final double quantity;
    private final double price;

    public Execution(Order incoming, Order resting, double quantity, double price) {
        this.incomingClOrdID = incoming.getClOrdID();
        this.restingClOrdID = resting.getClOrdID();
        this.symbol = incoming.getSymbol();
        this.quantity = quantity;
        this.price = price;
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

    public double getQuantity() {
        return quantity;
    }

    public double getPrice() {
        return price;
    }
}

