package org.example.util;

public record WindowLayoutProfile(
        double preferredWidth,
        double preferredHeight,
        double minimumWidth,
        double minimumHeight
) {
    public static WindowLayoutProfile forResource(String resourcePath) {
        if ("/view/Login.fxml".equals(resourcePath)) {
            return new WindowLayoutProfile(780.0, 520.0, 360.0, 420.0);
        }
        if ("/view/Register.fxml".equals(resourcePath)) {
            return new WindowLayoutProfile(780.0, 520.0, 560.0, 420.0);
        }
        if ("/view/AuctionList.fxml".equals(resourcePath)) {
            return new WindowLayoutProfile(1024.0, 640.0, 760.0, 480.0);
        }
        if ("/view/Dashboard.fxml".equals(resourcePath)) {
            return new WindowLayoutProfile(1280.0, 860.0, 980.0, 720.0);
        }
        return new WindowLayoutProfile(1120.0, 780.0, 840.0, 600.0);
    }
}
