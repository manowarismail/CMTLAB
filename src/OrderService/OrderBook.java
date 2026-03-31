package OrderService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

public class OrderBook {

    private final String symbol;

    // Bids: Sorted High to Low (Descending) - Best Bid is Highest Price
    private final NavigableMap<Double, List<Order>> bids = new ConcurrentSkipListMap<>(
            Collections.reverseOrder());
    // Asks: Sorted Low to High (Ascending) - Best Ask is Lowest Price
    private final NavigableMap<Double, List<Order>> asks = new ConcurrentSkipListMap<>();

    public OrderBook(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }

    // synchronized ensures only one thread can modify the book for this symbol at a time
    public synchronized List<Execution> match(Order incoming) {
        List<Execution> executions = new ArrayList<>();

        if (isBuy(incoming)) {
            // Attempt to match against the Asks (Sellers)
            matchOrder(incoming, asks, executions);
        } else {
            // Attempt to match against the Bids (Buyers)
            matchOrder(incoming, bids, executions);
        }

        // If the order is not fully filled after matching, add the remainder to the book
        if (incoming.getQuantity() > 0) {
            addToBook(incoming);
        }
        return executions;
    }

    private boolean isBuy(Order order) {
        char s = order.getSide();
        return s == '1' || s == 'B';
    }

    // Helper to add resting orders to the correct map
    private void addToBook(Order order) {
        NavigableMap<Double, List<Order>> side = isBuy(order) ? bids : asks;
        // ComputeIfAbsent creates the list if this is the first order at this price
        side.computeIfAbsent(order.getPrice(), k -> new LinkedList<>()).add(order);
    }

    private void matchOrder(Order incoming, NavigableMap<Double, List<Order>> bookSide, List<Execution> executions) {
        while (incoming.getQuantity() > 0 && !bookSide.isEmpty()) {
            Double bestPrice = bookSide.firstKey();

            boolean buy = isBuy(incoming);
            if (buy && incoming.getPrice() < bestPrice) {
                break;
            }
            if (!buy && incoming.getPrice() > bestPrice) {
                break;
            }

            List<Order> ordersAtLevel = bookSide.get(bestPrice);
            if (ordersAtLevel == null || ordersAtLevel.isEmpty()) {
                bookSide.remove(bestPrice);
                continue;
            }

            Order resting = ordersAtLevel.get(0);
            double tradeQty = Math.min(incoming.getQuantity(), resting.getQuantity());

            Execution exec = new Execution(incoming, resting, tradeQty, bestPrice);
            executions.add(exec);
            System.out.println("TRADE EXECUTED: " + incoming.getSymbol() + " " + formatQty(tradeQty)
                    + " shares @ $" + formatPrice(bestPrice));

            incoming.reduceQty(tradeQty);
            resting.reduceQty(tradeQty);

            if (resting.getQuantity() == 0) {
                ordersAtLevel.remove(0);
                if (ordersAtLevel.isEmpty()) {
                    bookSide.remove(bestPrice);
                }
            }
        }
    }

    private String formatQty(double qty) {
        long asLong = (long) qty;
        if (qty == asLong) {
            return String.valueOf(asLong);
        }
        return String.valueOf(qty);
    }

    private String formatPrice(double px) {
        long asLong = (long) px;
        if (px == asLong) {
            return String.valueOf(asLong);
        }
        return String.valueOf(px);
    }
}
