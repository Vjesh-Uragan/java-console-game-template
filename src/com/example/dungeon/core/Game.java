package com.example.dungeon.core;

import com.example.dungeon.model.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

public class Game {
    private final GameState state = new GameState();
    private final Map<String, Command> commands = new LinkedHashMap<>();

    static {
        WorldInfo.touch("Game");
    }

    public Game() {
        registerCommands();
        bootstrapWorld();
    }

    private void registerCommands() {
        commands.put("help", (ctx, a) -> System.out.println("Команды: " + String.join(", ", commands.keySet())));

        commands.put("about", (ctx, a) -> {
            System.out.println("DungeonMini v1.0 — консольная RPG");
            System.out.println("Используйте 'help' для списка команд.");
            System.out.println("Автор: Vjesh-Uragan");
        });

        commands.put("gc-stats", (ctx, a) -> {
            Runtime rt = Runtime.getRuntime();
            long free = rt.freeMemory(), total = rt.totalMemory(), used = total - free;
            System.out.println("Память: used=" + used + " free=" + free + " total=" + total);
        });

        commands.put("alloc", (ctx, a) -> {
            System.out.println("Выделяем 10000 строк...");
            List<String> garbage = new ArrayList<>();
            for (int i = 0; i < 10000; i++) {
                garbage.add("string_" + i + "_" + System.nanoTime());
            }
            System.out.println("Создано. Вызываем System.gc()...");
            System.gc(); // подсказка GC
            Runtime rt = Runtime.getRuntime();
            long used = rt.totalMemory() - rt.freeMemory();
            System.out.println("Память после GC: used=" + used);
        });

        commands.put("look", (ctx, a) -> System.out.println(ctx.getCurrent().describe()));

        commands.put("move", (ctx, a) -> {
            if (a.isEmpty()) {
                throw new InvalidCommandException("Укажите направление: north, south, east, west");
            }
            String dir = a.getFirst().toLowerCase(Locale.ROOT);
            Room current = ctx.getCurrent();

            // Особая логика для восточной двери в Лесу
            if (current.getName().equals("Лес") && "east".equals(dir)) {
                boolean hasKey = state.getPlayer().getInventory().stream()
                        .anyMatch(i -> i instanceof Key && i.getName().equals("Ключ от восточной двери"));
                if (!hasKey) {
                    throw new InvalidCommandException("Дверь заперта. Нужен ключ.");
                }
                // Удаляем ключ
                state.getPlayer().getInventory().removeIf(i -> i instanceof Key && i.getName().equals("Ключ от восточной двери"));
                System.out.println("Ключ использован. Дверь открыта.");
            }

            Room next = current.getNeighbors().get(dir);
            if (next == null) {
                throw new InvalidCommandException("Нет пути в направлении: " + dir);
            }
            state.setCurrent(next);
            System.out.println("Вы перешли в: " + next.getName());
            System.out.println(next.describe());
        });

        commands.put("take", (ctx, a) -> {
            if (a.isEmpty()) {
                throw new InvalidCommandException("Укажите название предмета");
            }
            String itemName = String.join(" ", a);
            Room current = ctx.getCurrent();
            Item item = current.getItems().stream()
                    .filter(i -> i.getName().equals(itemName))
                    .findFirst()
                    .orElse(null);
            if (item == null) {
                throw new InvalidCommandException("Предмет '" + itemName + "' не найден в этой комнате");
            }
            current.getItems().remove(item);
            ctx.getPlayer().getInventory().add(item);
            System.out.println("Взято: " + item.getName());
        });

        commands.put("inventory", (ctx, a) -> {
            List<Item> inv = ctx.getPlayer().getInventory();
            if (inv.isEmpty()) {
                System.out.println("Инвентарь пуст.");
                return;
            }
            Map<String, List<Item>> grouped = inv.stream()
                    .collect(Collectors.groupingBy(
                            item -> item.getClass().getSimpleName(),
                            Collectors.collectingAndThen(
                                    Collectors.toList(),
                                    list -> list.stream().sorted(Comparator.comparing(Item::getName)).toList()
                            )
                    ));
            System.out.println("Инвентарь:");
            grouped.forEach((type, items) -> {
                System.out.println("- " + type + " (" + items.size() + "): " +
                        items.stream().map(Item::getName).collect(Collectors.joining(", ")));
            });
        });

        commands.put("use", (ctx, a) -> {
            if (a.isEmpty()) {
                throw new InvalidCommandException("Укажите название предмета для использования");
            }
            String itemName = String.join(" ", a);
            Player p = ctx.getPlayer();
            Item item = p.getInventory().stream()
                    .filter(i -> i.getName().equals(itemName))
                    .findFirst()
                    .orElse(null);
            if (item == null) {
                throw new InvalidCommandException("Предмет '" + itemName + "' не найден в инвентаре");
            }
            item.apply(ctx);
        });

        commands.put("fight", (ctx, a) -> {
            Room current = ctx.getCurrent();
            Monster monster = current.getMonster();
            if (monster == null) {
                System.out.println("В комнате нет монстра.");
                return;
            }

            Player player = ctx.getPlayer();
            int playerDmg = player.getAttack();
            int monsterDmg = monster.getLevel() * 2;

            monster.setHp(monster.getHp() - playerDmg);
            System.out.println("Вы бьёте " + monster.getName() + " на " + playerDmg + ". HP монстра: " + Math.max(0, monster.getHp()));

            if (monster.getHp() <= 0) {
                System.out.println("Монстр повержен!");
                // Лут: ключ при первом убийстве волка
                if (monster.getName().equals("Волк")) {
                    Key key = new Key("Ключ от восточной двери");
                    current.getItems().add(key);
                } else {
                    Potion loot = new Potion("Зелье победы", 3);
                    current.getItems().add(loot);
                }
                current.setMonster(null);
                ctx.addScore(10);
                return;
            }

            player.setHp(player.getHp() - monsterDmg);
            System.out.println("Монстр отвечает на " + monsterDmg + ". Ваше HP: " + Math.max(0, player.getHp()));

            if (player.getHp() <= 0) {
                System.out.println("Вы погибли... Игра окончена.");
                System.exit(1);
            }
        });

        commands.put("save", (ctx, a) -> SaveLoad.save(ctx));
        commands.put("load", (ctx, a) -> SaveLoad.load(ctx));
        commands.put("scores", (ctx, a) -> SaveLoad.printScores());
        commands.put("exit", (ctx, a) -> {
            System.out.println("Пока!");
            System.exit(0);
        });
    }

    private void bootstrapWorld() {
        Player hero = new Player("Герой", 20, 5);
        state.setPlayer(hero);

        Room square = new Room("Площадь", "Каменная площадь с фонтаном.");
        Room forest = new Room("Лес", "Шелест листвы и птичий щебет.");
        Room cave = new Room("Пещера", "Темно и сыро.");
        Room temple = new Room("Заброшенный Храм", "Стены покрыты мхом, в воздухе пахнет древностью.");

        // Связи
        square.getNeighbors().put("north", forest);
        forest.getNeighbors().put("south", square);
        forest.getNeighbors().put("east", temple); // дверь, требующая ключа
        temple.getNeighbors().put("west", forest);
        forest.getNeighbors().put("secret", cave); // скрытый путь, не показывается в описании
        cave.getNeighbors().put("exit", forest);

        // Предметы и монстры
        forest.getItems().add(new Potion("Малое зелье", 5));
        forest.setMonster(new Monster("Волк", 1, 8));

        temple.getItems().add(new Weapon("Меч новичка", 3));

        state.setCurrent(square);
    }

    public void run() {
        System.out.println("DungeonMini (TEMPLATE). 'help' — команды.");
        try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in))) {
            while (true) {
                System.out.print("> ");
                String line = in.readLine();
                if (line == null) break;
                line = line.trim();
                if (line.isEmpty()) continue;
                List<String> parts = Arrays.asList(line.split("\\s+"));
                String cmd = parts.getFirst().toLowerCase(Locale.ROOT);
                List<String> args = parts.size() > 1 ? parts.subList(1, parts.size()) : List.of();
                Command c = commands.get(cmd);
                try {
                    if (c == null) throw new InvalidCommandException("Неизвестная команда: " + cmd);
                    c.execute(state, args);
                    state.addScore(1);
                } catch (InvalidCommandException e) {
                    System.out.println("Ошибка: " + e.getMessage());
                } catch (Exception e) {
                    System.out.println("Непредвиденная ошибка: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                    /*
                     * Ошибки компиляции (не скомпилируется):
                     *   int x = "hello"; // Incompatible types
                     *
                     * Ошибки выполнения (скомпилируется, но упадёт в рантайме):
                     *   int y = 10 / 0; // ArithmeticException: / by zero
                     */
                }
            }
        } catch (IOException e) {
            System.out.println("Ошибка ввода/вывода: " + e.getMessage());
        }
    }
}