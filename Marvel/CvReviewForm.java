import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.regex.Pattern;

public class CvReviewForm extends JFrame {
    private File cvFile;
    private final JLabel fileLabel = new JLabel("Belum ada file dipilih");

    // kolom: id (disembunyikan), Simpan (checkbox), Skill
    private final DefaultTableModel model = new DefaultTableModel(new Object[]{"id", "Simpan", "Skill"}, 0) {
        @Override
        public Class<?> getColumnClass(int c) {
            return c == 1 ? Boolean.class : Object.class;
        }

        @Override
        public boolean isCellEditable(int r, int c) {
            return c == 1;
        }
    };
    private final JTable table = new JTable(model);
    private final DefaultListModel<String> ownedModel = new DefaultListModel<>();

    public CvReviewForm() {
        super("SkillBridge - Unggah & Review CV");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(720, 460);
        setLocationRelativeTo(null);

        table.removeColumn(table.getColumnModel().getColumn(0));
        table.getColumnModel().getColumn(0).setMaxWidth(70);

        // Atas: pilih file
        JButton pickBtn = new JButton("Pilih CV (PDF)...");
        pickBtn.addActionListener(e -> chooseFile());
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(pickBtn);
        top.add(fileLabel);

        // Kiri: hasil ekstraksi
        JPanel left = new JPanel(new BorderLayout(4, 4));
        left.setBorder(BorderFactory.createTitledBorder("Hasil ekstraksi (centang yang benar)"));
        left.add(new JScrollPane(table), BorderLayout.CENTER);

        // Kanan: skill yang sudah terkonfirmasi
        JPanel right = new JPanel(new BorderLayout(4, 4));
        right.setBorder(BorderFactory.createTitledBorder("Keahlian terkonfirmasi"));
        right.add(new JScrollPane(new JList<>(ownedModel)), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setResizeWeight(0.6);

        // Bawah: aksi
        JButton saveBtn = new JButton("Konfirmasi & Simpan");
        saveBtn.addActionListener(e -> save());
        JButton backBtn = new JButton("Kembali");
        backBtn.addActionListener(e -> {
            new TargetKarierForm().setVisible(true);
            dispose();
        });
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.add(backBtn);
        bottom.add(saveBtn);

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        root.add(top, BorderLayout.NORTH);
        root.add(split, BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);
        setContentPane(root);

        loadOwned();
    }

    private void chooseFile() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("File PDF", "pdf"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        cvFile = fc.getSelectedFile();
        fileLabel.setText(cvFile.getName());
        extract();
    }

    // Ekstrak teks PDF, lalu cocokkan dengan katalog skill
    private void extract() {
        String text;
        try (PDDocument doc = Loader.loadPDF(cvFile)) {
            text = new PDFTextStripper().getText(doc);
        } catch (IOException ex) {
            error(ex);
            return;
        }
        if (text.isBlank()) {
            JOptionPane.showMessageDialog(this,
                    "PDF tidak memiliki teks yang bisa dibaca (kemungkinan hasil scan).",
                    "Ekstraksi gagal", JOptionPane.WARNING_MESSAGE);
            return;
        }

        model.setRowCount(0);
        try (Connection c = Db.get(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, name FROM skills ORDER BY name")) {
            while (rs.next()) {
                String name = rs.getString(2);
                // pencocokan kata utuh: "Java" tidak cocok dengan "JavaScript"
                Pattern p = Pattern.compile(
                        "(?<![\\p{L}\\p{N}])" + Pattern.quote(name) + "(?![\\p{L}\\p{N}])",
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                if (p.matcher(text).find()) model.addRow(new Object[]{rs.getInt(1), true, name});
            }
        } catch (Exception ex) {
            error(ex);
            return;
        }
        if (model.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "Tidak ada skill dari katalog yang ditemukan di CV ini.");
        }
    }

    // Simpan hanya skill yang dicentang user -> status confirmed
    private void save() {
        if (cvFile == null || model.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "Pilih CV dan pastikan ada hasil ekstraksi dulu.");
            return;
        }
        String upsert = "INSERT INTO user_skills (user_id, skill_id, status) VALUES (?, ?, 'confirmed') "
                + "ON CONFLICT (user_id, skill_id) DO UPDATE SET status = 'confirmed'";
        String evSql = "INSERT INTO evidence (user_id, type, title, url) VALUES (?, 'CV', ?, ?)";
        int saved = 0;
        try (Connection c = Db.get()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(upsert);
                 PreparedStatement ev = c.prepareStatement(evSql)) {
                for (int i = 0; i < model.getRowCount(); i++) {
                    if (Boolean.TRUE.equals(model.getValueAt(i, 1))) {
                        ps.setInt(1, Db.userId);
                        ps.setInt(2, (int) model.getValueAt(i, 0));
                        ps.addBatch();
                        saved++;
                    }
                }
                if (saved > 0) {
                    ps.executeBatch();
                    ev.setInt(1, Db.userId);
                    ev.setString(2, cvFile.getName());
                    ev.setString(3, cvFile.getAbsolutePath());
                    ev.executeUpdate();
                }
                c.commit();
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            }
        } catch (Exception ex) {
            error(ex);
            return;
        }
        model.setRowCount(0);
        loadOwned();
        JOptionPane.showMessageDialog(this, saved + " skill dikonfirmasi dan CV tersimpan sebagai evidence.");
    }

    private void loadOwned() {
        ownedModel.clear();
        String sql = "SELECT s.name FROM user_skills us JOIN skills s ON s.id = us.skill_id "
                + "WHERE us.user_id = ? AND us.status = 'confirmed' ORDER BY s.name";
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, Db.userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ownedModel.addElement(rs.getString(1));
            }
        } catch (Exception ex) {
            error(ex);
        }
    }

    private void error(Exception ex) {
        JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
    }
}