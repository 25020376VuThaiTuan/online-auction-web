package org.example.auction;

import org.example.model.Bid;
import org.example.model.Item;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class AuctionSession implements AuctionSubject {

    private static final int X_SECONDS = 10;
    private static final int Y_SECONDS = 60;

    private Item item;
    private AuctionStatus status;
    private List<AuctionObserver> observers = new ArrayList<>();
    private List<Bid> bids = new ArrayList<>();
    private double currentHighestBid;
    private LocalDateTime endTime;

    // Sử dụng 'final' để đảm bảo lock không bị thay đổi tham chiếu
    private final ReentrantLock lock = new ReentrantLock();

    // Hợp nhất các constructor lại để khởi tạo đầy đủ trạng thái
    public AuctionSession(Item item, double startingPrice, LocalDateTime endTime) {
        this.item = item;
        this.currentHighestBid = startingPrice;
        this.endTime = endTime;
        this.status = AuctionStatus.OPEN;
    }

    public void startAuction() {
        this.status = AuctionStatus.RUNNING;
    }

    public void finishAuction() {
        this.status = AuctionStatus.FINISHED;
    }

    public boolean placeBid(Bid bid) {
        lock.lock();
        try {
            if(status != AuctionStatus.RUNNING) {
                System.out.println("Error: The sesion is not started!");
                return false;
            }

            LocalDateTime now = LocalDateTime.now();

            // 2. Kiểm tra thời gian
            if (now.isAfter(endTime)) {
                System.out.println("The sesion is ended!");
                this.status = AuctionStatus.FINISHED; // Tự động cập nhật trạng thái
                return false;
            }

            // Giả định lớp Bid của bạn có phương thức getAmount()
            double newBidAmount = bid.getAmount();

            // 3. Kiểm tra tính hợp lệ của giá
            if (newBidAmount > currentHighestBid) {
                // 4. Cập nhật dữ liệu
                currentHighestBid = newBidAmount;
                bids.add(bid);
                System.out.println("Bid successfully! New price: " + currentHighestBid);

                // 5. Logic Anti-sniping
                long secondsRemaining = ChronoUnit.SECONDS.between(now, endTime);
                if (secondsRemaining <= X_SECONDS) {
                    endTime = endTime.plusSeconds(Y_SECONDS);
                    System.out.println("New time added! Time remaining: " + endTime);
                }
                if (secondsRemaining < 60) {
                    System.out.println("The session is about to end!Decide quickly or you will not have this gorgeous item");
                    notifyObservers();
                }

                // 6. Thông báo cho các Client (Observer Pattern)
                notifyObservers();

                return true;
            }else{
                // Logic for invalid bid price
                System.out.println("Bid rejected! Must be higher than " + currentHighestBid);
                return false;
            }
        } finally {
            // Luôn đặt unlock trong finally để tránh deadlock nếu có Exception xảy ra
            lock.unlock();
        }
    }
    @Override
    // Đưa hàm này ra ngoài cấp độ class (Class level)
    public void notifyObservers(){
        if (bids.isEmpty()) return;
        Bid lastBid = bids.get(bids.size() - 1);
        for (AuctionObserver o : observers) {
            o.onNewBid(lastBid);
        }
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    @Override
    public void addObserver(AuctionObserver observer) {
        observers.add(observer);
    }

    @Override
    public void removeObserver(AuctionObserver observer) {
        observers.remove(observer);
    }
    @Override
    public AuctionStatus getStatus() {
        return status;
    }
}