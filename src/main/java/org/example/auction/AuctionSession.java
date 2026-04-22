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
            // 1. Kiểm tra trạng thái session
            if (status != AuctionStatus.RUNNING) {
                System.out.println("Lỗi:Phiên chưa bắt đầu!");
                return false;
            }

            // 2. Kiểm tra thời gian
            LocalDateTime now = LocalDateTime.now();
            if (now.isAfter(endTime)) {
                System.out.println("Phiên kết thúc!");
                this.status = AuctionStatus.FINISHED;
                return false;
            }

            // ... (Chỗ này là logic check giá thầu ) ...

            // 6. Thông báo cho các Client (Observer Pattern)
            notifyObservers();
            return true;

        } finally {
            // Luôn đặt unlock trong finally để tránh deadlock
            lock.unlock();
        }
    } // Kết thúc hàm placeBid chuẩn ở đây

    // Đưa hàm này ra ngoài cấp độ class (Class level)
    public void notifyObservers() {
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


    public AuctionStatus getStatus() {
        return status;
    }
}