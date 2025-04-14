package org.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class Database {
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/bot";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "12345";

    // Сохранение данных пользователя
    public void saveUserHistory(String chatId, String gender, double height, double weight, double bmi) {
        String insertSQL = """
                INSERT INTO user_history (chat_id, gender, height, weight, bmi)
                VALUES (?, ?, ?, ?, ?);
                """;

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {
            pstmt.setString(1, chatId);
            pstmt.setString(2, gender);
            pstmt.setDouble(3, height);
            pstmt.setDouble(4, weight);
            pstmt.setDouble(5, bmi);
            pstmt.executeUpdate();
            System.out.println("Данные сохранены для chatId: " + chatId);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Получение истории расчетов пользователя
    public String getUserHistory(String chatId) {
        StringBuilder history = new StringBuilder();
        String selectSQL = """
                SELECT height, weight, bmi, date
                FROM user_history
                WHERE chat_id = ?
                ORDER BY date DESC;
                """;

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(selectSQL)) {
            pstmt.setString(1, chatId);
            ResultSet rs = pstmt.executeQuery();

            if (!rs.isBeforeFirst()) {
                return "История расчетов пуста.";
            }

            while (rs.next()) {
                double height = rs.getDouble("height");
                double weight = rs.getDouble("weight");
                double bmi = rs.getDouble("bmi");
                String date = rs.getString("date");

                history.append(String.format("""
                        Дата: %s
                        Рост: %.2f см
                        Вес: %.2f кг
                        ИМТ: %.2f
                        -------------------
                        """, date, height, weight, bmi));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return history.toString();
    }
}