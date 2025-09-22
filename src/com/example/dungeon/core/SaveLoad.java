package com.example.dungeon.core;

import com.example.dungeon.model.*;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class SaveLoad {
    private static final Path SAVE = Paths.get("save.txt");
    private static final Path SCORES = Paths.get("scores.csv");

    public static void save(GameState s) {
        try (BufferedWriter w = Files.newBufferedWriter(SAVE)) {
            Player p = s.getPlayer();

            // Формат: player;<name>;<hp>;<attack>
            w.write("player;" + p.getName() + ";" + p.getHp() + ";" + p.getAttack());
            w.newLine();

            // Инвентарь: Potion:Малое зелье,Key:Ключ от восточной двери
            String inv = p.getInventory().stream()
                    .map(i -> i.getClass().getSimpleName() + ":" + i.getName())
                    .collect(Collectors.joining(","));
            w.write("inventory;" + inv);
            w.newLine();

            // Текущая комната
            w.write("room;" + s.getCurrent().getName());
            w.newLine();

            System.out.println("Сохранено в " + SAVE.toAbsolutePath());

            // Записываем очки
            writeScore(p.getName(), s.getScore());

        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось сохранить игру", e);
        }
    }

    public static void load(GameState s) {
        if (!Files.exists(SAVE)) {
            System.out.println("Сохранение не найдено.");
            return;
        }

        try (BufferedReader r = Files.newBufferedReader(SAVE)) {
            Map<String, String> map = new HashMap<>();
            String line;
            while ((line = r.readLine()) != null) {
                String[] parts = line.split(";", 2);
                if (parts.length == 2) {
                    map.put(parts[0], parts[1]);
                }
            }

            Player p = s.getPlayer();

            // === ЗАГРУЗКА ИГРОКА ===
            String playerData = map.get("player");
            if (playerData == null) {
                System.out.println("Предупреждение: данные игрока отсутствуют. Используются значения по умолчанию.");
                p.setName("Герой");
                p.setHp(20);
                p.setAttack(5);
            } else {
                String[] pp = playerData.split(";");
                // Ожидаем минимум: [0] - не используется (player), [1] - имя, [2] - hp, [3] - attack
                if (pp.length < 4) {
                    System.out.println("Предупреждение: Используются значения по умолчанию.");
                    p.setName("Герой");
                    p.setHp(20);
                    p.setAttack(5);
                } else {
                    try {
                        p.setName(pp[1]);
                        p.setHp(Integer.parseInt(pp[2]));
                        p.setAttack(Integer.parseInt(pp[3]));
                    } catch (NumberFormatException e) {
                        System.out.println("Предупреждение: неверный формат чисел. Используются значения по умолчанию.");
                        p.setHp(20);
                        p.setAttack(5);
                    }
                }
            }

            // === ЗАГРУЗКА ИНВЕНТАРЯ ===
            p.getInventory().clear();
            String invData = map.get("inventory");
            if (invData != null && !invData.isBlank()) {
                for (String token : invData.split(",")) {
                    String[] parts = token.split(":", 2);
                    if (parts.length < 2) continue;

                    switch (parts[0]) {
                        case "Potion" -> p.getInventory().add(new Potion(parts[1], 5));
                        case "Key" -> p.getInventory().add(new Key(parts[1]));
                        case "Weapon" -> p.getInventory().add(new Weapon(parts[1], 3));
                        default -> System.out.println("Неизвестный тип предмета: " + parts[0]);
                    }
                }
            }

            // === ЗАГРУЗКА КОМНАТЫ (упрощённо) ===
            String roomName = map.getOrDefault("room", "Площадь");
            System.out.println("Игра загружена (упрощённо). Текущая комната: " + roomName);
            // Полная загрузка графа комнат — выходит за рамки ТЗ

            System.out.println("Игра загружена.");

        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось загрузить игру", e);
        }
    }

    public static void printScores() {
        if (!Files.exists(SCORES)) {
            System.out.println("Пока нет результатов.");
            return;
        }
        try (BufferedReader r = Files.newBufferedReader(SCORES)) {
            System.out.println("Таблица лидеров (топ-10):");
            r.lines().skip(1) // пропускаем заголовок
                    .map(line -> line.split(","))
                    .filter(parts -> parts.length >= 3)
                    .map(parts -> new Score(parts[1], Integer.parseInt(parts[2])))
                    .sorted(Comparator.comparingInt(Score::score).reversed())
                    .limit(10)
                    .forEach(s -> System.out.println(s.player() + " — " + s.score()));
        } catch (IOException e) {
            System.err.println("Ошибка чтения результатов: " + e.getMessage());
        } catch (NumberFormatException e) {
            System.err.println("Ошибка формата в scores.csv: " + e.getMessage());
        }
    }

    private static void writeScore(String player, int score) {
        try {
            boolean needsHeader = !Files.exists(SCORES);
            try (BufferedWriter w = Files.newBufferedWriter(SCORES, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                if (needsHeader) {
                    w.write("ts,player,score");
                    w.newLine();
                }
                w.write(LocalDateTime.now() + "," + player + "," + score);
                w.newLine();
            }
        } catch (IOException e) {
            System.err.println("Не удалось записать очки: " + e.getMessage());
        }
    }

    private record Score(String player, int score) {}
}