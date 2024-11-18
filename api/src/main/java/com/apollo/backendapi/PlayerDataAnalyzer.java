package com.apollo.backendapi;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import com.apollo.backendapi.pojos.PostPlayer;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class PlayerDataAnalyzer {
    // Use a thread-safe concurrent queue to accumulate incomplete players
    private final ConcurrentLinkedQueue<IncompletePlayerData> allIncompletePlayers = new ConcurrentLinkedQueue<>();

    private static class IncompletePlayerData {
        int id;
        String nickname;
        String firstName;
        String lastName;
        Integer age;
        String role;

        public IncompletePlayerData(PostPlayer player) {
            this.id = player.id;
            this.nickname = player.nickName;
            this.firstName = player.firstName;
            this.lastName = player.lastName;
            this.age = player.age != null ? player.age.years : null;
            this.role = mapRoleIdToRoleName(player.role != null ? player.role.id : null);
        }

        private String mapRoleIdToRoleName(Integer roleId) {
            if (roleId == null) {
                return null;
            }
            switch (roleId) {
                case 1:
                    return "top";
                case 2:
                    return "jungle";
                case 3:
                    return "mid";
                case 4:
                    return "bot";
                case 5:
                    return "support";
                default:
                    return null;
            }
        }

        public boolean hasMissingInfo() {
            return firstName == null || firstName.isEmpty() ||
                    lastName == null || lastName.isEmpty() ||
                    nickname == null || nickname.isEmpty() ||
                    age == null ||
                    role == null;
        }
    }

    // Method to accumulate players from each batch
    public void accumulatePlayers(List<PostPlayer> players) {
        players.parallelStream().forEach(player -> {
            IncompletePlayerData playerData = new IncompletePlayerData(player);
            if (playerData.hasMissingInfo()) {
                allIncompletePlayers.add(playerData);
            }
        });
    }

    // Method to write final Excel file after all batches are processed
    public void writeToExcel() {
        if (allIncompletePlayers.isEmpty()) {
            System.out.println("No incomplete player data to export.");
            return;
        }

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Incomplete Player Data");

            // Create cell styles
            CellStyle lockedStyle = createLockedCellStyle(workbook);
            CellStyle unlockedHighlightedStyle = createUnlockedHighlightedCellStyle(workbook);

            // Create header row with locked style
            Row headerRow = sheet.createRow(0);
            String[] columns = { "ID", "IGN", "First Name", "Last Name", "Age", "Role" };
            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(lockedStyle);
            }

            // Populate data rows
            int rowNum = 1;
            for (IncompletePlayerData player : allIncompletePlayers) {
                Row row = sheet.createRow(rowNum++);

                // ID - assuming always present
                Cell idCell = row.createCell(0);
                idCell.setCellValue(player.id);
                idCell.setCellStyle(lockedStyle);

                // IGN (Nickname)
                Cell ignCell = row.createCell(1);
                if (player.nickname != null && !player.nickname.isEmpty()) {
                    ignCell.setCellValue(player.nickname);
                    ignCell.setCellStyle(lockedStyle);
                } else {
                    ignCell.setCellStyle(unlockedHighlightedStyle);
                }

                // First Name
                Cell firstNameCell = row.createCell(2);
                if (player.firstName != null && !player.firstName.isEmpty()) {
                    firstNameCell.setCellValue(player.firstName);
                    firstNameCell.setCellStyle(lockedStyle);
                } else {
                    firstNameCell.setCellStyle(unlockedHighlightedStyle);
                }

                // Last Name
                Cell lastNameCell = row.createCell(3);
                if (player.lastName != null && !player.lastName.isEmpty()) {
                    lastNameCell.setCellValue(player.lastName);
                    lastNameCell.setCellStyle(lockedStyle);
                } else {
                    lastNameCell.setCellStyle(unlockedHighlightedStyle);
                }

                // Age
                Cell ageCell = row.createCell(4);
                if (player.age != null) {
                    ageCell.setCellValue(player.age);
                    ageCell.setCellStyle(lockedStyle);
                } else {
                    ageCell.setCellStyle(unlockedHighlightedStyle);
                }

                // Role
                Cell roleCell = row.createCell(5);
                if (player.role != null && !player.role.isEmpty()) {
                    roleCell.setCellValue(player.role);
                    roleCell.setCellStyle(lockedStyle);
                } else {
                    roleCell.setCellStyle(unlockedHighlightedStyle);
                }

            }

            // Autosize columns for better readability
            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // Protect the sheet to enforce cell locking
            // You can set a password by replacing "password" with your desired password
            sheet.protectSheet("password"); // If you don't want a password, you can pass an empty string "" or remove
                                            // the parameter

            // Write the workbook to a file
            try (FileOutputStream fileOut = new FileOutputStream("incomplete_player_data.xlsx")) {
                workbook.write(fileOut);
            }

            System.out.println("Successfully exported " + allIncompletePlayers.size() +
                    " players with incomplete data to incomplete_player_data.xlsx");

            // Clear the queue after writing to prevent memory leaks
            allIncompletePlayers.clear();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Helper method to create a locked cell style
    private CellStyle createLockedCellStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setLocked(true);
        // Optionally, you can set other styles like borders or fonts here
        return style;
    }

    // Helper method to create an unlocked cell style with highlighting
    private CellStyle createUnlockedHighlightedCellStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setLocked(false); // Unlock the cell to allow editing

        // Set background color (e.g., yellow) to highlight
        style.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // Optionally, you can set borders or other styles
        return style;
    }
}
