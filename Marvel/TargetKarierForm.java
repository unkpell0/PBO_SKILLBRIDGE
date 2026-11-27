import javax.swing.*;
import java.awt.*;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class TargetKarierForm extends JFrame {
    private final JComboBox<Item> careerBox = new JComboBox<>();
    private final JTextArea descArea = new JTextArea(5, 30);
    private final Map<Integer, String> descriptions = new HashMap<>();

    public TargetKarierForm() {
        super("SkillBridge - Target Karier");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        descArea.setEditable(false);
        descArea.setLineWrap(true);
        descArea.setWrapStyleWord(true);

        JPanel top = new JPanel(new GridLayout(0, 1, 6, 6));
        top.add(new JLabel("Halo, " + Db.userName + "! Pilih target karier:"));
        top.add(careerBox);

        JButton saveBtn = new JButton("Simpan");
        saveBtn.addActionListener(e -> save());
        JButton nextBtn = new JButton("Lanjut ke Unggah CV");
        nextBtn.addActionListener(e -> {
            new CvReviewForm().setVisible(true);
            dispose();
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(saveBtn);
        buttons.add(nextBtn);

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        root.add(top, BorderLayout.NORTH);
        root.add(new JScrollPane(descArea), BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);

        careerBox.addActionListener(e -> {
            Item it = (Item) careerBox.getSelectedItem();
            descArea.setText(it == null ? "" : descriptions.getOrDefault(it.id(), ""));
        });

        loadCareers();
        pack();
        setLocationRelativeTo(null);
    }

    private void loadCareers() {
        try (Connection c = Db.get()) {
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT id, name, description FROM careers ORDER BY name")) {
                while (rs.next()) {
                    careerBox.addItem(new Item(rs.getInt("id"), rs.getString("name")));
                    descriptions.put(rs.getInt("id"), rs.getString("description"));
                }
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT target_career_id FROM users WHERE id = ?")) {
                ps.setInt(1, Db.userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int current = rs.getInt(1);
                        if (!rs.wasNull()) {
                            for (int i = 0; i < careerBox.getItemCount(); i++) {
                                if (careerBox.getItemAt(i).id() == current) careerBox.setSelectedIndex(i);
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
            error(ex);
        }
    }

    private void save() {
        Item it = (Item) careerBox.getSelectedItem();
        if (it == null) return;
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement("UPDATE users SET target_career_id = ? WHERE id = ?")) {
            ps.setInt(1, it.id());
            ps.setInt(2, Db.userId);
            ps.executeUpdate();
            JOptionPane.showMessageDialog(this, "Target karier disimpan: " + it.label());
        } catch (Exception ex) {
            error(ex);
        }
    }

    private void error(Exception ex) {
        JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
    }
}