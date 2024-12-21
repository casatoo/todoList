import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.*;

public class UserManager {
    private String currentUser; // 현재 로그인한 사용자 이름
    private String dbUrl;
    private String dbUser;
    private String dbPassword;
    private ExecutorService executorService;

    public UserManager(String dbUrl, String dbUser, String dbPassword) {
        this.dbUrl = dbUrl;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
        this.currentUser = null; // 초기화
        this.executorService = Executors.newSingleThreadExecutor(); // 단일 스레드 풀
    }

    public boolean registerUser(String username, String password) {
        Callable<Boolean> task = () -> {
            try (Connection connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
                String hashedPassword = hashPassword(password);
                String query = "INSERT INTO users (username, password) VALUES (?, ?)";
                PreparedStatement statement = connection.prepareStatement(query);
                statement.setString(1, username);
                statement.setString(2, hashedPassword);
                statement.executeUpdate();
                return true; // 회원가입 성공
            }
        };

        return executeWithTimeout(task, "회원가입 중 오류가 발생했습니다.");
    }

    public boolean authenticateUser(String username, String password) {
        Callable<Boolean> task = () -> {
            try (Connection connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
                String hashedPassword = hashPassword(password);
                String query = "SELECT id FROM users WHERE username = ? AND password = ?";
                PreparedStatement statement = connection.prepareStatement(query);
                statement.setString(1, username);
                statement.setString(2, hashedPassword);
                ResultSet resultSet = statement.executeQuery();

                if (resultSet.next()) {
                    currentUser = username; // 로그인 성공 시 현재 사용자 설정
                    return true;
                }
            }
            return false;
        };

        return executeWithTimeout(task, "로그인 중 오류가 발생했습니다.");
    }

    public int getUserId(String username) {
        Callable<Integer> task = () -> {
            try (Connection connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
                String query = "SELECT id FROM users WHERE username = ?";
                PreparedStatement statement = connection.prepareStatement(query);
                statement.setString(1, username);
                ResultSet resultSet = statement.executeQuery();

                if (resultSet.next()) {
                    return resultSet.getInt("id"); // 사용자 ID 반환
                }
            }
            return -1; // 사용자 ID를 찾지 못한 경우
        };

        return executeWithTimeout(task, "사용자 정보를 불러오는 중 오류가 발생했습니다.");
    }

    private <T> T executeWithTimeout(Callable<T> task, String errorMessage) {
        Future<T> future = executorService.submit(task);
        try {
            return future.get(10, TimeUnit.SECONDS); // 10초 제한
        } catch (TimeoutException e) {
            future.cancel(true); // 작업 취소
            throw new RuntimeException("작업이 시간 초과되었습니다.");
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(errorMessage);
        }
    }

    public void logoutUser() {
        currentUser = null; // 현재 사용자 초기화
    }

    public String getCurrentUser() {
        return currentUser;
    }

    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown(); // 스레드 풀 종료
        }
    }
}