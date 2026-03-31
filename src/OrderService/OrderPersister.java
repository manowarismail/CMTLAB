package OrderService;

import java.util.concurrent.BlockingQueue;

public class OrderPersister implements Runnable {

    private final BlockingQueue<Object> dbQueue;

    public OrderPersister(BlockingQueue<Object> dbQueue) {
        this.dbQueue = dbQueue;
    }

    @Override
    public void run() {
        System.out.println("[OrderPersister] Worker started. Waiting for orders...");

        while (true) {
            try {
                Object item = dbQueue.take(); // blocks until work is available
                if (item instanceof Order) {
                    Order order = (Order) item;
                    System.out.println("[OrderPersister] Persisting order: " + order.getClOrdID());
                    JdbcOrderStore.insertOrder(order);
                } else if (item instanceof Execution) {
                    Execution execution = (Execution) item;
                    System.out.println("[OrderPersister] Persisting execution: " + execution.getExecId());
                    JdbcOrderStore.insertExecution(execution);
                } else {
                    System.err.println("[OrderPersister] Unknown queue item type: " + item);
                }
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