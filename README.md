## Remote MySQL

See [REMOTE_MYSQL_SETUP.md](REMOTE_MYSQL_SETUP.md) for server, schema, firewall, API, and JavaFX client steps.

## Local run commands

Start the JavaFX desktop app with the default developer run path:

```sh
mvn exec:java
```

The Maven exec plugin points this command at `org.example.Launcher`, the same JavaFX entrypoint used by `mvn javafx:run`.

Start the standalone API server only when you need that process directly:

```sh
mvn exec:java@api-server
```

Bảng phân chia công việc:
1. Nguyễn Anh Tài:
   Đã làm: Xây dựng cây OOP, quản lý data, xử lý exception và logic cơ bản, làm giao diện sơ khai
2. Đàm Minh Quý: Làm giao diện sơ khai, xây dựng auction class
   Nhiệm vụ tiếp theo: Xử lý đa luồng, realtime, server và database
3. Chu Bá Sơn: Phụ trách thiết kế JavaFX, FXML,...; fix bug conflict
4. Vũ Thái Tuấn: Socket/API, Unit test và CI/CD


<img width="1024" height="559" alt="image" src="https://github.com/user-attachments/assets/156727b2-92aa-41b3-b69b-fd30af6b3cc9" />
