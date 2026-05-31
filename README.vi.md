# Online Auction Web

## 1. Mô tả bài toán và phạm vi hệ thống

Dự án là một nền tảng đấu giá trực tuyến gồm:

- Client desktop bằng JavaFX
- Server HTTP API phục vụ đăng nhập, quản lý người dùng, ví, sản phẩm và phiên đấu giá
- Chế độ local demo để chạy offline, hoặc chế độ remote dùng MySQL

Hệ thống hỗ trợ 3 vai trò chính:

- `Bidder`: tham gia đấu giá, nạp/rút tiền, đặt giá, dùng auto-bid
- `Seller`: gửi sản phẩm và tạo phiên đấu giá
- `Admin`: duyệt sản phẩm, quản lý người dùng và theo dõi kết quả settlement

Phạm vi đã triển khai:

- Đăng ký, đăng nhập, đăng xuất
- Quản lý hồ sơ và ví
- Gửi sản phẩm, duyệt sản phẩm và xem danh sách đấu giá
- Đặt giá thầu, auto-bid và giữ tiền đặt cọc tham gia đấu giá
- Ghi nhận settlement và thông báo cho người dùng
- Chế độ local demo và chế độ remote MySQL

## 2. Công nghệ, môi trường chạy và yêu cầu cài đặt

### Công nghệ sử dụng

- Java 21
- Maven
- JavaFX 21
- `com.sun.net.httpserver.HttpServer`
- JDBC MySQL Connector
- H2 cho test
- JUnit 5
- BCrypt cho mật khẩu và mã khôi phục

### Môi trường chạy

- Windows, Linux, macOS
- Các lệnh Maven bên dưới chạy được trên cả 3 hệ điều hành
- Chế độ remote/MySQL cần MySQL 8+ hoặc dịch vụ MySQL tương thích

### Yêu cầu cài đặt

Kiểm tra cấu hình máy:

```sh
java -version
mvn -version
```

Yêu cầu tối thiểu:

- JDK 21
- Apache Maven 3.9+
- MySQL chỉ cần khi chạy chế độ remote

## 3. Cấu trúc thư mục / module chính

- `src/main/java/org/example/controller`: các controller JavaFX
- `src/main/java/org/example/service`: nghiệp vụ chính
- `src/main/java/org/example/model`: entity và object của miền nghiệp vụ
- `src/main/java/org/example/dao`: truy cập dữ liệu và JDBC
- `src/main/java/org/example/server`: HTTP API server, routing, session và realtime broker
- `src/main/java/org/example/client`: client gọi API
- `src/main/java/org/example/util`: các tiện ích dùng chung
- `src/main/resources/view`: FXML và CSS của giao diện
- `src/main/resources/db/migration`: migration schema
- `src/test/java`: unit test và integration test
- `scripts`: script hỗ trợ chạy local, remote và Azure
- `schema.sql`: schema MySQL
- `REMOTE_MYSQL_SETUP.md`: tài liệu cấu hình remote MySQL
- `AZURE_CONNECTION.md`: tài liệu kết nối Azure MySQL

## 4. Câu lệnh dòng lệnh để chạy chương trình

### Cách chạy chuẩn, dùng được trên Windows/Linux/macOS

Mở 2 terminal riêng.

Terminal 1, chạy server:

```sh
mvn exec:java@api-server
```

Terminal 2, chạy client:

```sh
mvn exec:java
```

### Chạy local demo bằng script

Windows PowerShell:

```powershell
.\scripts\Start-LocalApi.ps1
.\scripts\Start-LocalClient.ps1
```

Linux/macOS:

```sh
sh scripts/start-local-api.sh
sh scripts/start-local-client.sh
```

### Chạy chế độ remote/MySQL

Thiết lập các biến môi trường sau trước khi chạy server:

- `AUCTION_DB_URL`
- `AUCTION_DB_USER`
- `AUCTION_DB_PASSWORD`

Sau đó chạy server:

```sh
mvn exec:java@api-server
```

Trỏ client về API đang chạy bằng tham số JVM đa nền tảng:

```sh
mvn -Dauction.api.baseUrl=http://localhost:8081/api exec:java
```

Nếu muốn dùng script remote:

Windows PowerShell:

```powershell
.\scripts\Start-RemoteApi.ps1 -EnvFile .\scripts\internet-api.env
.\scripts\Start-RemoteClient.ps1 -EnvFile .\scripts\internet-api.env
```

Linux/macOS:

```sh
sh scripts/start-remote-api.sh scripts/internet-api.env
sh scripts/start-remote-client.sh scripts/internet-api.env
```

## 5. Hướng dẫn chạy Server/Client theo thứ tự cụ thể

Thứ tự khuyến nghị:

1. Chạy Server trước
2. Chờ server báo đang lắng nghe trên port `8081` hoặc port bạn cấu hình
3. Chạy Client sau
4. Đăng nhập bằng tài khoản phù hợp

### Tài khoản demo local

- `bidder` / `bid123`
- `seller` / `sell123`
- `admin` / `admin123`

## 6. Danh sách chức năng đã hoàn thành

- Đăng ký tài khoản bidder hoặc seller
- Đăng nhập, đăng xuất, lấy thông tin người dùng hiện tại
- Khôi phục và đặt lại mật khẩu
- Quản lý hồ sơ cá nhân
- Quản lý ví: PIN, xác thực ví, nạp/rút tiền, tài khoản liên kết
- Khôi phục và đặt lại PIN ví
- Đăng sản phẩm và tạo phiên đấu giá
- Duyệt sản phẩm cho admin
- Xem danh sách đấu giá, chi tiết đấu giá và danh sách sản phẩm chờ duyệt
- Đặt giá thầu và hỗ trợ auto-bid
- Giữ tiền đặt cọc tham gia đấu giá
- Kết thúc đấu giá và ghi nhận settlement
- Thông báo cho người dùng
- Realtime event stream cho cập nhật đấu giá
- Hỗ trợ local demo storage và remote MySQL

## 7. Ghi chú

- Nếu dùng MySQL remote, hãy áp dụng `schema.sql` trước khi khởi động server.
- Nếu cổng `8081` đang bị chiếm, hãy đổi bằng `AUCTION_API_PORT` hoặc tham số `--port`.
- `mvn exec:java` là lệnh chạy client mặc định của repository này.
