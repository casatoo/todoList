import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class TodoApp extends JFrame {
    private JTextField taskEntry;
    private DefaultListModel<String> listModel;
    private JList<String> taskList;
    private Map<String, Integer> settings;
    private int currentUserId;
    private String dbUrl = System.getenv("DB_URL");
    private String dbUser = System.getenv("DB_USER");
    private String dbPassword = System.getenv("DB_PASSWORD");
    private final UserManager userManager;

    public TodoApp() {
        setIcon();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        // 설정 파일에서 데이터베이스 정보 로드
        loadDatabaseConfig();
        // 설정 로드
        loadSettings(); // settings 초기화
        // UserManager 초기화
        userManager = new UserManager(dbUrl, dbUser, dbPassword); // UserManager 초기화
        showLoginDialog();
    }

    private void setIcon() {
        // 아이콘 설정
        try {
            File iconFile = new File("todo_icon.png");
            if (iconFile.exists()) {
                ImageIcon icon = new ImageIcon(iconFile.getAbsolutePath());
                setIconImage(icon.getImage());
            } else {
                System.err.println("아이콘 파일을 찾을 수 없습니다: " + iconFile.getAbsolutePath());
            }
        } catch (Exception e) {
            System.err.println("아이콘을 로드하는데 실패했습니다: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void showTodoList() {
        if (currentUserId == 0) {
            // 사용자 ID가 없으면 로그인 화면을 다시 띄운다
            showLoginDialog();
            return;
        }

        setupUI(); // UI 초기화
        loadTasks(); // 할 일 목록 로드
        setTitle("To-Do");
        centerWindow();
        setIcon();
    }

    private void loadDatabaseConfig() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        Properties properties = new Properties();
        try (FileInputStream input = new FileInputStream("config.properties")) {
            properties.load(input);
            dbUrl = properties.getProperty("db.url");
            dbUser = properties.getProperty("db.user");
            dbPassword = properties.getProperty("db.password");
        } catch (IOException e) {
            e.printStackTrace();
            // 환경 변수로 대체
            dbUrl = System.getenv("DB_URL");
            dbUser = System.getenv("DB_USER");
            dbPassword = System.getenv("DB_PASSWORD");
        }
    }

    private void showLoginDialog() {

        if (currentUserId != 0) {
            // 이미 로그인된 상태에서 로그인 다이얼로그를 띄우지 않음
            showTodoList();
            return;
        }

        // 로그인 다이얼로그 생성
        JDialog loginDialog = new JDialog(this, "로그인", true);

        loginDialog.setSize(340, 200); // 크기 조정
        loginDialog.setLayout(new BorderLayout(10, 10)); // 여백 추가

        // 메인 패널 생성
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS)); // 컴포넌트 세로 배치
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20)); // 안쪽 여백 설정

        // 입력 필드 패널
        JPanel inputPanel = new JPanel();
        inputPanel.setLayout(new GridLayout(2, 2, 10, 10)); // 3행 2열 레이아웃

        JTextField usernameField = new JTextField();
        JPasswordField passwordField = new JPasswordField();

        // 레이블과 입력 필드 추가
        inputPanel.add(new JLabel("사용자 이름:"));
        inputPanel.add(usernameField);
        inputPanel.add(new JLabel("비밀번호:"));
        inputPanel.add(passwordField);

        // 버튼 패널
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 10, 10)); // 중앙 정렬, 간격 10px

        JButton loginButton = new JButton("로그인");
        JButton cancelButton = new JButton("취소");
        JButton registerButton = new JButton("회원가입");

        styleButton(loginButton);
        styleButton(cancelButton);
        styleButton(registerButton);

        buttonPanel.add(loginButton);
        buttonPanel.add(registerButton);
        buttonPanel.add(cancelButton);

        // 로그인 동작
        loginButton.addActionListener(e -> {
            String username = usernameField.getText();
            String password = new String(passwordField.getPassword());
            boolean authenticated = userManager.authenticateUser(username, password);

            if (authenticated) {
                currentUserId = userManager.getUserId(username);
                dispose(); // 로그인 성공 시 로그인 창 닫기
                showTodoList(); // 로그인 후 TodoList 화면 표시
            } else {
                JOptionPane.showMessageDialog(loginDialog, "로그인 실패: 사용자 이름 또는 비밀번호가 잘못되었습니다.", "오류", JOptionPane.ERROR_MESSAGE);
            }
        });

        // 취소 버튼 동작
        cancelButton.addActionListener(e -> {
            System.exit(0); // 프로그램 종료
        });

        loginDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE); // 기본 닫기 동작 제거
        loginDialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                System.exit(0); // 프로그램 종료
            }
        });

        // 회원가입 버튼 동작
        registerButton.addActionListener(e -> showRegistrationDialog());

        // 다이얼로그에 패널 추가
        mainPanel.add(inputPanel);
        mainPanel.add(Box.createVerticalStrut(20)); // 버튼과 입력 필드 사이 간격
        mainPanel.add(buttonPanel);
        loginDialog.add(mainPanel, BorderLayout.CENTER);

        // 다이얼로그 설정
        loginDialog.setLocationRelativeTo(this); // 화면 중앙에 위치
        loginDialog.setVisible(true);

    }

    // 버튼 스타일 설정 메서드
    private void styleButton(JButton button) {
        button.setPreferredSize(new Dimension(80, 20));
        button.setBackground(new Color(100, 100, 100)); // 버튼 배경색
        button.setForeground(Color.BLACK); // 버튼 글자색
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); // 커서 변경

        // 버튼 호버 효과
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setBackground(new Color(100, 150, 200)); // 호버 시 색상 변경
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setBackground(new Color(100, 100, 100)); // 버튼 배경색
            }
        });
    }

    private void showRegistrationDialog() {
        // 회원가입 다이얼로그 생성
        JDialog registrationDialog = new JDialog(this, "회원가입", true);
        registrationDialog.setSize(300, 200); // 크기 조정
        registrationDialog.setLayout(new BorderLayout(10, 10));

        // 메인 패널
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20)); // 여백 설정

        // 입력 필드 패널
        JPanel inputPanel = new JPanel();
        inputPanel.setLayout(new GridLayout(2, 2, 10, 10));

        JTextField usernameField = new JTextField();
        JPasswordField passwordField = new JPasswordField();

        inputPanel.add(new JLabel("사용자 이름:"));
        inputPanel.add(usernameField);
        inputPanel.add(new JLabel("비밀번호:"));
        inputPanel.add(passwordField);

        // 버튼 패널
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 10, 10));

        JButton registerButton = new JButton("회원가입");
        JButton cancelButton = new JButton("취소");

        styleButton(registerButton);
        styleButton(cancelButton);

        buttonPanel.add(registerButton);
        buttonPanel.add(cancelButton);

        // 회원가입 버튼 동작
        registerButton.addActionListener(e -> {
            String username = usernameField.getText();
            String password = new String(passwordField.getPassword());
            boolean registered = userManager.registerUser(username, password);

            if (registered) {
                JOptionPane.showMessageDialog(this, "회원가입 성공!", "알림", JOptionPane.INFORMATION_MESSAGE);
                registrationDialog.dispose(); // 성공 시 닫기
            } else {
                JOptionPane.showMessageDialog(this, "회원가입 실패!", "오류", JOptionPane.ERROR_MESSAGE);
            }
        });

        // 취소 버튼 동작
        cancelButton.addActionListener(e -> registrationDialog.dispose());

        // 다이얼로그에 패널 추가
        mainPanel.add(inputPanel);
        mainPanel.add(Box.createVerticalStrut(20));
        mainPanel.add(buttonPanel);
        registrationDialog.add(mainPanel, BorderLayout.CENTER);

        // 다이얼로그 설정
        registrationDialog.setLocationRelativeTo(this);
        registrationDialog.setVisible(true);
    }

   private void setupUI() {
       JTabbedPane tabbedPane = new JTabbedPane();
        
        // 인 탭
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        // 헤더
        String user = userManager.getCurrentUser(); // 사용자 이름을 가져와서 표시
        JLabel titleLabel = new JLabel(user + " Todo-List");
        titleLabel.setFont(new Font("맑은 고딕", Font.BOLD, 24));
        mainPanel.add(titleLabel, BorderLayout.NORTH);
        
        // 입력 패널
        JPanel inputPanel = new JPanel(new BorderLayout(5, 0));
        taskEntry = new JTextField();
        taskEntry.setPreferredSize(new Dimension(0, 30));
        JButton addButton = new JButton("추가");
        inputPanel.add(taskEntry, BorderLayout.CENTER);
        inputPanel.add(addButton, BorderLayout.EAST);
        
        // 리스트 초기화
        listModel = new DefaultListModel<>(); // listModel 초기화
        taskList = new JList<>(listModel);
        taskList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scrollPane = new JScrollPane(taskList);
        
        // 버튼 패널
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton deleteButton = new JButton("선택 삭제");
        JButton clearButton = new JButton("전체 삭제");
        JButton logoutButton = new JButton("로그아웃"); // 로그아웃 버튼 추가

        buttonPanel.add(deleteButton);
        buttonPanel.add(clearButton);
        buttonPanel.add(logoutButton);
        
        // 중앙 패널
        JPanel centerPanel = new JPanel(new BorderLayout(10, 10));
        centerPanel.add(inputPanel, BorderLayout.NORTH);
        centerPanel.add(scrollPane, BorderLayout.CENTER);
        centerPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        mainPanel.add(centerPanel, BorderLayout.CENTER);
        
        // 탭 추가
        tabbedPane.addTab("TO_DO", mainPanel);

        add(tabbedPane);
        
        // 이벤트 리스너
        addButton.addActionListener(e -> addTask());
        deleteButton.addActionListener(e -> deleteTask());
        clearButton.addActionListener(e -> clearTasks());
        logoutButton.addActionListener(e -> handleLogout()); // 로그아웃 이벤트

        taskEntry.addActionListener(e -> addTask());
    }

    // 로그아웃 처리
    private void handleLogout() {
        dispose(); // 현재 프레임 닫기
        // 로그아웃 후 TodoApp을 새로 생성하여 로그인 화면을 표시
        userManager.logoutUser();
        currentUserId = 0; // 사용자 정보 초기화
        // TodoApp을 새로 만들어 로그인 화면을 표시
        TodoApp app = new TodoApp();
        app.showLoginDialog(); // 로그인 화면 표시
    }
    
    private void addTask() {
        String task = taskEntry.getText().trim();
        if (!task.isEmpty()) {
            LocalDateTime now = LocalDateTime.now();
            String timeStamp = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            listModel.addElement("[" + timeStamp + "] " + task);
            taskEntry.setText("");
            saveTasksToServer(); // 데이터베이스에 저장
        } else {
            JOptionPane.showMessageDialog(this, 
                "할 일을 입력해주세요!", "경고", 
                JOptionPane.WARNING_MESSAGE);
        }
    }
    
    private void deleteTask() {
        int selectedIndex = taskList.getSelectedIndex();
        if (selectedIndex != -1) {
            listModel.remove(selectedIndex);
            saveTasksToServer(); // 데이터베이스에 저장
        } else {
            JOptionPane.showMessageDialog(this, 
                "삭제할 항목을 선택해주세요!", "경고", 
                JOptionPane.WARNING_MESSAGE);
        }
    }
    
    private void clearTasks() {
        int result = JOptionPane.showConfirmDialog(this,
        "모든 항목을 삭제하시겠습니까?", "확인",
        JOptionPane.YES_NO_OPTION);
        
        if (result == JOptionPane.YES_OPTION) {
            listModel.clear();
            saveTasksToServer(); // 데이터베이스에 저장
        }
    }
    
    private void loadTasks() {
        listModel.clear(); // 기존 목록 초기화
        loadTasksFromServer(); // 데이터베이스에서 불러오기
    }
    
    private void loadSettings() {
        settings = new HashMap<>(); // settings 초기화
        try (BufferedReader reader = new BufferedReader(new FileReader("settings.txt"))) {
            settings.put("width", Integer.parseInt(reader.readLine()));
            settings.put("height", Integer.parseInt(reader.readLine()));
        } catch (Exception e) {
            // 본 설정
            settings.put("width", 600);
            settings.put("height", 700);
        }
    }
    
    private void centerWindow() {
        setSize(settings.get("width"), settings.get("height"));
        setLocationRelativeTo(null);
    }

    private void saveTasksToServer() {
        try (Connection connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
            String deleteQuery = "DELETE FROM tasks WHERE user_id = ?"; // 현재 사용자 작업 삭제
            try (PreparedStatement deleteStatement = connection.prepareStatement(deleteQuery)) {
                deleteStatement.setInt(1, currentUserId);
                deleteStatement.executeUpdate();
            }

            String insertQuery = "INSERT INTO tasks (task, user_id) VALUES (?, ?)";
            try (PreparedStatement insertStatement = connection.prepareStatement(insertQuery)) {
                for (int i = 0; i < listModel.size(); i++) {
                    insertStatement.setString(1, listModel.get(i));
                    insertStatement.setInt(2, currentUserId); // 현재 사용자 ID 저장
                    insertStatement.executeUpdate();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadTasksFromServer() {
        try (Connection connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
            String query = "SELECT task FROM tasks WHERE user_id = ?"; // 현재 사용자 작업 불러오기
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setInt(1, currentUserId); // 현재 사용자 ID 설정
                try (ResultSet resultSet = statement.executeQuery()) {
                    listModel.clear(); // 기존 목록 초기화
                    while (resultSet.next()) {
                        String task = resultSet.getString("task");
                        listModel.addElement(task); // JList에 추가
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> {
            TodoApp app = new TodoApp();
            app.setVisible(true);

            // 창 닫을 때 할 일 목록 저장 및 스레드 풀 종료
            app.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    app.saveTasksToServer(); // 서버에 작업 저장
                    app.userManager.shutdown(); // 스레드 풀 종료
                    System.exit(0); // 프로그램 종료
                }
            });
        });
    }

} 