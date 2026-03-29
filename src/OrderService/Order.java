package OrderService;

import java.util.UUID;

public class Order {

    private final String orderId;  // Auto-generated unique ID
    private final String clOrdID;
    private final String symbol;
    private final char side;       // 'B' or 'S'
    private final double price;
    private final double quantity;
    private final String status;

    // Constructor
    public Order(String clOrdID, String symbol, char side, double price, double quantity, String status) {
        this.orderId = UUID.randomUUID().toString(); // unique DB ID
        this.clOrdID = clOrdID;
        this.symbol = symbol;
        this.side = side;
        this.price = price;
        this.quantity = quantity;
        this.status = status;
    }

    // Getters
    public String getOrderId() { return orderId; }
    public String getClOrdID() { return clOrdID; }
    public String getSymbol() { return symbol; }
    public char getSide() { return side; }
    public double getPrice() { return price; }
    public double getQuantity() { return quantity; }
    public String getStatus() { return status; }

    @Override
    public String toString() {
        return "Order{" +
                "orderId='" + orderId + '\'' +
                ", clOrdID='" + clOrdID + '\'' +
                ", symbol='" + symbol + '\'' +
                ", side=" + side +
                ", price=" + price +
                ", quantity=" + quantity +
                ", status='" + status + '\'' +
                '}';
    }
}