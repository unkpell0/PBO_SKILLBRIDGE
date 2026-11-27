import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class Db {
    // Ganti sesuai koneksi PostgreSQL kamu
    static final String URL = "jdbc:postgresql://localhost:5432/pbo_skillbridge";
    static final String USER = "postgres";
    static final String PASS = "1234";

    // Sesi pengguna (tanpa form login: memakai akun demo)
    public static int userId;
    public static String userName;

    public static Connection get() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }

    public static void useDemoUser() throws SQLException {
        try (Connection c = get();
             PreparedStatement ps = c.prepareStatement("SELECT id, name FROM users WHERE email = ?")) {
            ps.setString(1, "demo@skillbridge.id");
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Akun demo belum ada. Jalankan schema.sql dulu.");
                userId = rs.getInt(1);
                userName = rs.getString(2);
            }
        }
    }
}

// Item untuk JComboBox: simpan id, tampilkan label
record Item(int id, String label) {
    @Override
    public String toString() {
        return label;
    }
}