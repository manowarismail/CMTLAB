package OrderService;

import java.util.concurrent.BlockingQueue;

public class OrderPersister implements Runnable {

    private final BlockingQueue<Order> dbQueue;

    public OrderPersister(BlockingQueue<Order> dbQueue) {
        this.dbQueue = dbQueue;
    }

    @Override
    public void run() {
        System.out.println("[OrderPersister] Worker started. Waiting for orders...");

        while (true) {
            try {
                Order order = dbQueue.take(); // blocks until an order is available
                System.out.println("[OrderPersister] Persisting order: " + order.getClOrdID());
                JdbcOrderStore.insertOrder(order);
            } catch (InterruptedException e) {
                System.err.println("[OrderPersister] Interrupted!");
                e.printStackTrace();
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("[OrderPersister] Unexpected exception!");
                e.printStackTrace();
            }
        }
    }
}