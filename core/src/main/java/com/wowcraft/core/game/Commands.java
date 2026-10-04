package com.wowcraft.core.game;

import com.wowcraft.core.combat.Formulas;
import com.wowcraft.core.instance.DungeonDef;
import com.wowcraft.core.instance.Dungeons;
import com.wowcraft.core.item.Currency;
import com.wowcraft.core.item.EquipSlot;
import com.wowcraft.core.item.EquipType;
import com.wowcraft.core.item.ItemData;
import com.wowcraft.core.item.LootGenerator;
import com.wowcraft.core.mythic.Affix;
import com.wowcraft.core.mythic.AffixSchedule;
import com.wowcraft.core.mythic.MythicScore;
import com.wowcraft.core.net.C2S;
import com.wowcraft.core.net.S2C;
import com.wowcraft.core.npc.Difficulty;
import com.wowcraft.core.player.PlayerProfile;
import com.wowcraft.core.spec.Spec;
import com.wowcraft.core.spec.WowClass;
import com.wowcraft.core.util.L10n;
import com.wowcraft.core.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The /wow command (also reachable through the UI). */
final class Commands {
    private final GameServer server;

    Commands(GameServer server) {
        this.server = server;
    }

    private static L10n t(String en, String ru) {
        return L10n.of(en, ru);
    }

    List<L10n> run(PlayerSession s, String input, boolean op) {
        List<L10n> out = new ArrayList<>();
        String[] a = input.isEmpty() ? new String[0] : input.split("\\s+");
        String cmd = a.length == 0 ? "help" : a[0].toLowerCase(Locale.ROOT);
        String arg1 = a.length > 1 ? a[1] : null, arg2 = a.length > 2 ? a[2] : null, arg3 = a.length > 3 ? a[3] : null;
        PlayerProfile p = s.profile;
        switch (cmd) {
            case "help", "?" -> help(out);
            case "class", "spec" -> {
                if (arg1 == null) {
                    open(s, "class_select");
                    break;
                }
                Spec spec = Spec.byId(arg1.toLowerCase(Locale.ROOT));
                if (spec == null) {
                    WowClass c = WowClass.byId(arg1.toLowerCase(Locale.ROOT));
                    if (c != null) spec = arg2 != null ? findSpec(c, arg2) : Spec.of(c).get(0);
                }
                if (spec == null) {
                    out.add(t("Unknown class/spec. Examples: /wow class warrior arms, /wow spec frost_mage",
                            "Неизвестный класс/специализация. Пример: /wow class warrior arms, /wow spec frost_mage"));
                    break;
                }
                server.chooseClass(s, spec);
            }
            case "classes" -> {
                for (WowClass c : WowClass.values()) {
                    StringBuilder en = new StringBuilder(c.id() + ": "), ru = new StringBuilder(c.id() + ": ");
                    for (Spec sp : Spec.of(c)) {
                        en.append(sp.id()).append(" (").append(sp.role.name().toLowerCase()).append(") ");
                        ru.append(sp.id()).append(" (").append(sp.role.name().toLowerCase()).append(") ");
                    }
                    out.add(t(en.toString(), ru.toString()));
                }
            }
            case "talents" -> open(s, "talents");
            case "character", "char" -> open(s, "character");
            case "invite", "inv" -> {
                if (arg1 != null) server.groups.invite(s, arg1);
            }
            case "accept" -> server.groups.accept(s);
            case "decline" -> server.groups.decline(s);
            case "party" -> {
                if ("leave".equalsIgnoreCase(arg1)) server.groups.leave(s.uuid, true);
                else server.groups.sendGroup(s.uuid);
            }
            case "kick" -> {
                if (arg1 != null) server.groups.kick(s, arg1);
            }
            case "promote" -> {
                if (arg1 != null) server.groups.promote(s, arg1);
            }
            case "raid" -> server.groups.convertToRaid(s);
            case "role" -> server.groups.setRole(s, arg1);
            case "ready", "readycheck", "rc" -> server.groups.readyCheck(s);
            case "bot" -> {
                if ("remove".equalsIgnoreCase(arg1) && arg2 != null) server.groups.kick(s, arg2);
                else server.lfg.addBot(s, arg1);
            }
            case "bots", "fill" -> server.lfg.fillWithBots(s);
            case "dungeons", "instances" -> {
                for (DungeonDef d : Dungeons.all()) {
                    if (d.type != DungeonDef.Type.DUNGEON && d.type != DungeonDef.Type.RAID) continue;
                    StringBuilder diffs = new StringBuilder();
                    for (Difficulty df : d.difficulties) diffs.append(df.name().toLowerCase()).append(' ');
                    out.add(t(d.id + " [" + d.shortName + "] " + d.name.en() + " — " + diffs.toString().trim(),
                            d.id + " [" + d.shortName + "] " + d.name.ru() + " — " + diffs.toString().trim()));
                }
            }
            case "dungeon", "enter", "instance" -> {
                DungeonDef d = arg1 != null ? findDungeon(arg1) : null;
                if (d == null) {
                    out.add(t("Usage: /wow dungeon <id> [normal|heroic|mythic|mythic_plus|lfr|raid_normal|raid_heroic|raid_mythic]",
                            "Использование: /wow dungeon <id> [normal|heroic|mythic|mythic_plus|lfr|raid_normal|raid_heroic|raid_mythic]"));
                    break;
                }
                Difficulty df = arg2 != null ? parseDifficulty(arg2, d) : d.difficulties.get(0);
                if (df == null) {
                    out.add(t("Unknown difficulty.", "Неизвестная сложность."));
                    break;
                }
                server.instances.requestEnter(s, d, df);
            }
            case "key", "keystone" -> {
                if (p.keystone == null) out.add(t("You have no keystone.", "У вас нет ключа."));
                else {
                    DungeonDef d = Dungeons.get(p.keystone.dungeonId);
                    out.add(t("Keystone: " + d.name.en() + " +" + p.keystone.level + "  (start: /wow dungeon " + d.id + " mythic_plus)",
                            "Ключ: " + d.name.ru() + " +" + p.keystone.level + "  (запуск: /wow dungeon " + d.id + " mythic_plus)"));
                }
            }
            case "m+", "mplus", "mythic" -> {
                if (p.keystone == null) out.add(t("You have no keystone.", "У вас нет ключа."));
                else server.instances.requestEnter(s, Dungeons.get(p.keystone.dungeonId), Difficulty.MYTHIC_PLUS);
            }
            case "start" -> server.instances.startKey(s);
            case "leave" -> {
                if (s.unit.instanceId != null || server.pvp.matchOfSession(s) != null) server.instances.leave(s);
                else server.groups.leave(s.uuid, true);
            }
            case "release" -> server.instances.release(s);
            case "reset" -> server.instances.reset(s);
            case "queue", "lfd" -> server.lfg.queue(s, arg1 == null ? "random" : arg1, arg2, arg3, a.length > 4 && a[4].startsWith("f"));
            case "followers" -> server.lfg.queue(s, arg1 == null ? "random" : arg1, arg2 == null ? "normal" : arg2, arg3, true);
            case "lfr" -> server.lfg.queue(s, arg1 == null ? "random" : arg1, "lfr", arg2, false);
            case "unqueue", "leavequeue" -> server.lfg.leaveQueue(s);
            case "list", "listkey" -> server.lfg.listKey(s, arg1 != null ? String.join(" ", java.util.Arrays.copyOfRange(a, 1, a.length)) : null, 0);
            case "delist" -> server.lfg.delist(s);
            case "lfg" -> {
                server.lfg.sendLfg(s.uuid);
                open(s, "group_finder");
            }
            case "apply" -> {
                if (arg1 != null) server.lfg.apply(s, arg1, arg2);
            }
            case "pvp", "arena", "bg" -> {
                String bracket = cmd.equals("bg") ? (arg1 != null ? arg1 : "bg") : arg1;
                if (bracket == null) {
                    out.add(t("Usage: /wow pvp <2v2|3v3|5v5|shuffle|ctf|domination|bg> [rated]",
                            "Использование: /wow pvp <2v2|3v3|5v5|shuffle|ctf|domination|bg> [rated]"));
                    break;
                }
                server.pvp.queue(s, bracket, "rated".equalsIgnoreCase(cmd.equals("bg") ? arg2 : arg2));
            }
            case "vault" -> {
                server.send(s, Input.vault(p));
                open(s, "vault");
            }
            case "claim" -> {
                C2S.VaultClaim v = new C2S.VaultClaim();
                v.index = arg1 != null ? parseInt(arg1, 0) : 0;
                server.handle(s.uuid, v);
            }
            case "score", "rating", "io" -> {
                double r = MythicScore.totalRating(p);
                out.add(t("Mythic+ rating: " + r, "Рейтинг M+: " + r));
                for (var en : p.mythicBest.entrySet()) {
                    DungeonDef d = Dungeons.get(en.getKey());
                    PlayerProfile.MythicBest b = en.getValue();
                    out.add(t("  " + (d != null ? d.name.en() : en.getKey()) + ": +" + b.level + " " + Mth.formatTime(b.time) + (b.timed ? "" : " (over time)") + " — " + b.score,
                            "  " + (d != null ? d.name.ru() : en.getKey()) + ": +" + b.level + " " + Mth.formatTime(b.time) + (b.timed ? "" : " (не в срок)") + " — " + b.score));
                }
                for (var en : p.pvp.entrySet()) {
                    out.add(t("  PvP " + en.getKey() + ": " + en.getValue().rating + " (" + en.getValue().won + "/" + en.getValue().played + ")",
                            "  PvP " + en.getKey() + ": " + en.getValue().rating + " (" + en.getValue().won + "/" + en.getValue().played + ")"));
                }
            }
            case "affixes" -> {
                for (Affix af : AffixSchedule.weekly(server.week)) out.add(t(af.name.en() + ": " + af.description.en(), af.name.ru() + ": " + af.description.ru()));
            }
            case "stats" -> server.send(s, Sync.stats(s.unit));
            case "meter", "dps" -> {
                S2C.Meter m = Input.meter(server, s, arg1);
                for (S2C.MeterRow r : m.rows) {
                    double dps = m.duration > 0 ? r.damage / m.duration : 0, hps = m.duration > 0 ? r.healing / m.duration : 0;
                    out.add(t(r.name + ": " + Mth.shortNumber(dps) + " DPS, " + Mth.shortNumber(hps) + " HPS",
                            r.name + ": " + Mth.shortNumber(dps) + " урона/с, " + Mth.shortNumber(hps) + " исц./с"));
                }
            }
            case "leaderboard", "top" -> {
                S2C.Leaderboard lb = Input.leaderboard(server, arg1);
                for (S2C.LeaderboardRow r : lb.rows) {
                    DungeonDef d = Dungeons.get(r.dungeon);
                    out.add(t((d != null ? d.shortName : r.dungeon) + " +" + r.level + " " + Mth.formatTime(r.time) + (r.timed ? "" : "*") + " " + String.join(", ", r.party),
                            (d != null ? d.shortName : r.dungeon) + " +" + r.level + " " + Mth.formatTime(r.time) + (r.timed ? "" : "*") + " " + String.join(", ", r.party)));
                }
                for (int i = 0; i < lb.topPlayers.size(); i++) out.add(t((i + 1) + ". " + lb.topPlayers.get(i) + " — " + lb.topRatings.get(i),
                        (i + 1) + ". " + lb.topPlayers.get(i) + " — " + lb.topRatings.get(i)));
            }
            case "currency", "money", "wallet" -> {
                for (Currency c : Currency.values()) {
                    long v = p.currency(c);
                    if (v == 0) continue;
                    String val = c == Currency.GOLD ? Currency.formatGold(v) : String.valueOf(v);
                    out.add(t(c.name.en() + ": " + val, c.name.ru() + ": " + val));
                }
            }
            case "upgrade" -> {
                C2S.Upgrade up = new C2S.Upgrade();
                up.slot = arg1;
                server.handle(s.uuid, up);
            }
            case "unequip" -> {
                C2S.Unequip un = new C2S.Unequip();
                un.slot = arg1;
                server.handle(s.uuid, un);
            }
            // ---------------------------------------------------------- operator commands
            case "setlevel" -> {
                if (!op) return deny(out);
                p.level = Math.max(1, Math.min(Formulas.MAX_LEVEL, parseInt(arg1, p.level)));
                p.xp = 0;
                server.applyCharacter(s);
                out.add(t("Level set to " + p.level, "Уровень: " + p.level));
            }
            case "givekey" -> {
                if (!op) return deny(out);
                DungeonDef d = arg1 != null ? findDungeon(arg1) : null;
                if (d == null) d = Dungeons.get(Dungeons.mythicPool().get(0));
                p.keystone = new PlayerProfile.Keystone(d.id, Math.max(2, parseInt(arg2, 2)));
                s.characterDirty = true;
                out.add(t("Keystone: " + d.name.en() + " +" + p.keystone.level, "Ключ: " + d.name.ru() + " +" + p.keystone.level));
            }
            case "gear" -> {
                if (!op) return deny(out);
                Spec spec = p.spec();
                if (spec == null) break;
                int ilvl = parseInt(arg1, server.config.starterItemLevel);
                var eq = Rewards.fullSet(spec, ilvl, server.rng, "gm", true);
                for (var en : eq.all().entrySet()) {
                    if (en.getKey().isVanillaSlot()) server.platform.equip(s.uuid, en.getKey(), en.getValue());
                    else {
                        ItemData old = p.extraSlots.put(en.getKey().name(), en.getValue());
                        if (old != null) server.platform.giveItem(s.uuid, old);
                    }
                }
                server.applyCharacter(s);
                out.add(t("Equipped item level " + ilvl + " gear.", "Надето снаряжение уровня " + ilvl + "."));
            }
            case "item" -> {
                if (!op) return deny(out);
                Spec spec = p.spec();
                EquipType type = arg1 != null ? EquipType.byName(arg1) : null;
                if (spec == null || type == null) {
                    out.add(t("Usage: /wow item <head|neck|...|trinket|one_hand|two_hand> [ilvl]", "Использование: /wow item <тип> [ilvl]"));
                    break;
                }
                ItemData d = LootGenerator.generate(spec, type, parseInt(arg2, 130), server.rng, "gm", "starter");
                Rewards.giveLoot(server, s, List.of(d), t("GM", "ГМ"));
            }
            case "give" -> {
                if (!op) return deny(out);
                Currency c = arg1 != null ? Currency.byName(arg1) : null;
                if (c == null) break;
                p.addCurrency(c, parseInt(arg2, 100));
                s.characterDirty = true;
            }
            case "xp" -> {
                if (!op) return deny(out);
                Rewards.giveXp(server, s, parseInt(arg1, 1000));
            }
            case "weekly" -> {
                if (!op) return deny(out);
                server.week++;
                for (PlayerSession o : server.sessions()) server.weeklyCheck(o);
                out.add(t("Weekly reset simulated.", "Еженедельный сброс выполнен."));
            }
            default -> out.add(t("Unknown command. /wow help", "Неизвестная команда. /wow help"));
        }
        return out;
    }

    private List<L10n> deny(List<L10n> out) {
        out.add(t("Operator only.", "Только для операторов."));
        return out;
    }

    private void open(PlayerSession s, String screen) {
        S2C.OpenScreen o = new S2C.OpenScreen();
        o.screen = screen;
        server.send(s, o);
    }

    static int parseInt(String s, int def) {
        if (s == null) return def;
        try {
            return Integer.parseInt(s.replace("+", ""));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    static Spec findSpec(WowClass c, String name) {
        String n = name.toLowerCase(Locale.ROOT);
        for (Spec s : Spec.of(c)) {
            if (s.id().equals(n) || s.id().startsWith(n) || s.name.en().toLowerCase(Locale.ROOT).startsWith(n) || s.name.ru().toLowerCase(Locale.ROOT).startsWith(n))
                return s;
        }
        return null;
    }

    static DungeonDef findDungeon(String key) {
        DungeonDef d = Dungeons.get(key.toLowerCase(Locale.ROOT));
        if (d != null) return d;
        for (DungeonDef x : Dungeons.all()) if (x.shortName.equalsIgnoreCase(key)) return x;
        return null;
    }

    static Difficulty parseDifficulty(String s, DungeonDef d) {
        String n = s.toLowerCase(Locale.ROOT);
        if (d.isRaid()) {
            switch (n) {
                case "lfr" -> {
                    return Difficulty.LFR;
                }
                case "normal", "n" -> {
                    return Difficulty.RAID_NORMAL;
                }
                case "heroic", "hc", "h" -> {
                    return Difficulty.RAID_HEROIC;
                }
                case "mythic", "m" -> {
                    return Difficulty.RAID_MYTHIC;
                }
                default -> {
                }
            }
        }
        if (n.equals("m+") || n.equals("mplus") || n.equals("key")) return Difficulty.MYTHIC_PLUS;
        if (n.equals("hc") || n.equals("h")) return Difficulty.HEROIC;
        if (n.equals("m0") || n.equals("m")) return Difficulty.MYTHIC;
        return Difficulty.byName(n);
    }

    private static void help(List<L10n> out) {
        out.add(t("§6WoWCraft commands:", "§6Команды WoWCraft:"));
        out.add(t("/wow class <class> [spec] — choose class (or open the menu)", "/wow class <класс> [спек] — выбрать класс (или открыть меню)"));
        out.add(t("/wow talents | character — screens (also N / C keys)", "/wow talents | character — окна (также клавиши N / C)"));
        out.add(t("/wow invite <name> | accept | decline | party leave | kick | promote | raid | role <tank|healer|dps> | ready",
                "/wow invite <ник> | accept | decline | party leave | kick | promote | raid | role <tank|healer|dps> | ready"));
        out.add(t("/wow bot <tank|healer|dps|spec> | bots — add follower bots to your group", "/wow bot <tank|healer|dps|спек> | bots — добавить ботов в группу"));
        out.add(t("/wow dungeons — list; /wow dungeon <id> <difficulty> — enter with your group", "/wow dungeons — список; /wow dungeon <id> <сложность> — войти с группой"));
        out.add(t("/wow queue [dungeon|random] [normal|heroic] [role] — dungeon finder; /wow followers; /wow lfr",
                "/wow queue [подземелье|random] [normal|heroic] [роль] — поиск подземелий; /wow followers; /wow lfr"));
        out.add(t("/wow key | m+ | start | list [title] | lfg | apply <id> — Mythic+", "/wow key | m+ | start | list [название] | lfg | apply <id> — Эпохальный+"));
        out.add(t("/wow release | leave | reset — instances", "/wow release | leave | reset — подземелья"));
        out.add(t("/wow pvp <2v2|3v3|5v5|shuffle|ctf|domination|bg> [rated] — PvP queue", "/wow pvp <2v2|3v3|5v5|shuffle|ctf|domination|bg> [rated] — очередь PvP"));
        out.add(t("/wow vault | claim <n> | score | affixes | meter | top | currency | upgrade <slot> | stats",
                "/wow vault | claim <n> | score | affixes | meter | top | currency | upgrade <слот> | stats"));
    }
}
