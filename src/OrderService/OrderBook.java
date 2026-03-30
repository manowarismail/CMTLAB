package OrderService;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListMap;

public class OrderBook {

    private final String symbol;

    // Bids: Sorted High to Low (Descending) - Best Bid is Highest Price
    private final ConcurrentSkipListMap<Double, List<Order>> bids = new ConcurrentSkipListMap<>(
            Collections.reverseOrder());
    // Asks: Sorted Low to High (Ascending) - Best Ask is Lowest Price
    private final ConcurrentSkipListMap<Double, List<Order>> asks = new ConcurrentSkipListMap<>();

    public OrderBook(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }

    // Methods to add/remove orders will be added in Lab 7
}
