package OrderService;

public final class Security {

    private final String symbol;
    private final String securityType;
    private final String description;
    private final String underlying;
    private final int lotSize;

    public Security(String symbol, String securityType, String description, String underlying, int lotSize) {
        this.symbol = symbol;
        this.securityType = securityType;
        this.description = description;
        this.underlying = underlying;
        this.lotSize = lotSize;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getSecurityType() {
        return securityType;
    }

    public String getDescription() {
        return description;
    }

    public String getUnderlying() {
        return underlying;
    }

    public int getLotSize() {
        return lotSize;
    }
}
