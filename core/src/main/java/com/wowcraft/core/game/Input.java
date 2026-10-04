package com.wowcraft.core.game;

import com.wowcraft.core.combat.CastResult;
import com.wowcraft.core.combat.CombatEngine;
import com.wowcraft.core.combat.UnitState;
import com.wowcraft.core.content.ClassKit;
import com.wowcraft.core.content.Content;
import com.wowcraft.core.content.Registry;
import com.wowcraft.core.instance.DungeonDef;
import com.wowcraft.core.instance.Dungeons;
import com.wowcraft.core.item.EquipSlot;
import com.wowcraft.core.item.Equipment;
import com.wowcraft.core.item.ItemData;
import com.wowcraft.core.item.ItemUpgrades;
import com.wowcraft.core.net.C2S;
import com.wowcraft.core.net.S2C;
import com.wowcraft.core.npc.Difficulty;
import com.wowcraft.core.player.CharacterBuilder;
import com.wowcraft.core.player.GreatVault;
import com.wowcraft.core.player.PlayerProfile;
import com.wowcraft.core.spec.Spec;
import com.wowcraft.core.spec.WowClass;
import com.wowcraft.core.spell.Ability;
import com.wowcraft.core.talent.TalentNode;
import com.wowcraft.core.talent.TalentTree;
import com.wowcraft.core.util.L10n;
import com.wowcraft.core.util.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Handles client -> server messages. */
final class Input {
    private Input() {
    }

    static void handle(GameServer server, PlayerSession s, Object msg) {
        CombatEngine engine = server.engine;
        UnitState u = s.unit;
        if (msg instanceof C2S.Cast c) cast(server, s, c);
        else if (msg instanceof C2S.Release) engine.releaseEmpower(u);
        else if (msg instanceof C2S.CancelCast) engine.cancelCast(u, false);
        else if (msg instanceof C2S.Target t) engine.setTarget(u, t.entityId < 0 ? null : engine.unit(t.entityId));
        else if (msg instanceof C2S.ChooseClass c) chooseClass(server, s, c);
        else if (msg instanceof C2S.Talent t) talent(server, s, t);
        else if (msg instanceof C2S.SetBar b) setBar(server, s, b);
        else if (msg instanceof C2S.GroupAction g) group(server, s, g);
        else if (msg instanceof C2S.LfgAction l) lfg(server, s, l);
        else if (msg instanceof C2S.InstanceAction i) instance(server, s, i);
        else if (msg instanceof C2S.PvpAction p) pvp(server, s, p);
        else if (msg instanceof C2S.Request r) request(server, s, r);
        else if (msg instanceof C2S.VaultClaim v) vaultClaim(server, s, v);
        else if (msg instanceof C2S.Upgrade up) upgrade(server, s, up);
        else if (msg instanceof C2S.UseItem use) useItem(server, s, use);
        else if (msg instanceof C2S.Unequip un) unequip(server, s, un);
        else if (msg instanceof C2S.Interact in) interact(server, s, in);
        else if (msg instanceof C2S.Hello h) hello(server, s, h);
    }

    // ------------------------------------------------------------------ combat

    private static void cast(GameServer server, PlayerSession s, C2S.Cast c) {
        UnitState u = s.unit;
        if (s.ghost) return;
        String id = c.ability;
        if ((id == null || id.isEmpty()) && c.slot >= 0) {
            Spec spec = u.spec;
            if (spec == null) return;
            String[] bar = s.profile.barFor(spec);
            if (c.slot >= bar.length) return;
            id = bar[c.slot];
        }
        if (id == null || id.isEmpty()) return;
        String resolved = server.engine.resolveAbility(u, id);
        if (!u.knownAbilities.contains(id) && !u.knownAbilities.contains(resolved)) {
            server.onError(u, CastResult.UNKNOWN, Registry.ability(id));
            return;
        }
        UnitState target = c.targetId >= 0 ? server.engine.unit(c.targetId) : null;
        if (target != null && !server.engine.sameWorld(u, target)) target = null;
        Vec3 point = c.hasPoint ? new Vec3(c.x, c.y, c.z) : null;
        server.engine.cast(u, id, target, point);
    }

    private static void useItem(GameServer server, PlayerSession s, C2S.UseItem use) {
        UnitState u = s.unit;
        String ability = switch (use.which == null ? "" : use.which) {
            case "trinket_1", "trinket_2" -> u.tags.get(use.which) instanceof String str ? str : null;
            case "potion" -> "healing_potion";
            case "damage_potion" -> "damage_potion";
            case "healthstone" -> "healthstone";
            case "medallion" -> "gladiators_medallion";
            default -> null;
        };
        if (ability != null) server.engine.cast(u, ability, u.target(), null);
    }

    // ------------------------------------------------------------------ character

    private static void chooseClass(GameServer server, PlayerSession s, C2S.ChooseClass c) {
        Spec spec = Spec.byId(c.spec);
        if (spec == null) {
            WowClass wc = WowClass.byId(c.wowClass);
            if (wc == null) return;
            spec = Spec.of(wc).get(0);
        }
        server.chooseClass(s, spec);
    }

    private static void talent(GameServer server, PlayerSession s, C2S.Talent t) {
        PlayerProfile p = s.profile;
        Spec spec = p.spec();
        if (spec == null) return;
        if (s.unit.inCombat()) {
            server.msg(s, L10n.of("You can't change talents in combat.", "Нельзя менять таланты в бою."), 0xFFFF4040);
            return;
        }
        ClassKit kit = Content.kit(spec.wowClass);
        List<String> current = p.talentsFor(spec);
        if (t.reset) {
            current.clear();
        } else if (t.node != null) {
            TalentNode node = CharacterBuilder.findNode(kit, spec, t.node);
            if (node == null) return;
            Set<String> chosen = new LinkedHashSet<>(current);
            boolean hero = kit.heroOptions.contains(node);
            if (hero) {
                if (p.level < TalentTree.HERO_LEVEL) return;
                for (TalentNode h : kit.heroOptions) chosen.remove(h.id);
                if (t.learn) chosen.add(node.id);
            } else {
                TalentTree tree = kit.classTree != null && kit.classTree.node(node.id) != null ? kit.classTree : kit.specTrees.get(spec);
                if (tree == null) return;
                if (t.learn) {
                    if (!tree.canLearn(chosen, node.id, p.level)) {
                        server.msg(s, L10n.of("You can't learn that talent yet.", "Этот талант пока нельзя изучить."), 0xFFFF4040);
                        return;
                    }
                    chosen.add(node.id);
                } else {
                    if (!tree.canUnlearn(chosen, node.id)) {
                        server.msg(s, L10n.of("Other talents depend on that one.", "От этого таланта зависят другие."), 0xFFFF4040);
                        return;
                    }
                    chosen.remove(node.id);
                }
            }
            current.clear();
            current.addAll(CharacterBuilder.validTalents(kit, spec, p.level, chosen));
        }
        server.applyCharacter(s);
        s.characterDirty = true;
    }

    private static void setBar(GameServer server, PlayerSession s, C2S.SetBar b) {
        Spec spec = s.profile.spec();
        if (spec == null) return;
        String[] bar = s.profile.barFor(spec);
        if (b.slot < 0 || b.slot >= bar.length) return;
        if (b.ability == null || b.ability.isEmpty()) {
            bar[b.slot] = null;
        } else {
            Ability a = Registry.ability(b.ability);
            if (a == null || a.passive) return;
            if (!s.unit.knownAbilities.contains(b.ability)) return;
            // swapping: if already on the bar, move it
            for (int i = 0; i < bar.length; i++) if (b.ability.equals(bar[i])) bar[i] = bar[b.slot];
            bar[b.slot] = b.ability;
        }
        s.characterDirty = true;
    }

    // ------------------------------------------------------------------ social

    private static void group(GameServer server, PlayerSession s, C2S.GroupAction g) {
        GroupManager gm = server.groups;
        switch (g.action == null ? "" : g.action) {
            case "invite" -> gm.invite(s, g.name);
            case "accept" -> gm.accept(s);
            case "decline" -> gm.decline(s);
            case "leave" -> gm.leave(s.uuid, true);
            case "kick" -> gm.kick(s, g.name);
            case "promote" -> gm.promote(s, g.name);
            case "role" -> gm.setRole(s, g.value);
            case "ready_check" -> gm.readyCheck(s);
            case "ready" -> gm.answerReady(s, true);
            case "not_ready" -> gm.answerReady(s, false);
            case "raid" -> gm.convertToRaid(s);
            case "add_bot" -> server.lfg.addBot(s, g.value);
            case "fill_bots" -> server.lfg.fillWithBots(s);
            case "remove_bot" -> gm.kick(s, g.name);
            default -> gm.sendGroup(s.uuid);
        }
    }

    private static void lfg(GameServer server, PlayerSession s, C2S.LfgAction l) {
        LfgManager lfg = server.lfg;
        switch (l.action == null ? "" : l.action) {
            case "queue" -> lfg.queue(s, l.dungeon, l.difficulty, l.role, l.followers);
            case "leave" -> lfg.leaveQueue(s);
            case "list_key" -> lfg.listKey(s, l.title, l.minRating);
            case "delist" -> lfg.delist(s);
            case "apply" -> lfg.apply(s, l.listing, l.role);
            case "accept" -> lfg.acceptApplicant(s, l.applicant, true);
            case "decline" -> lfg.acceptApplicant(s, l.applicant, false);
            case "fill_bots" -> lfg.fillWithBots(s);
            case "add_bot" -> lfg.addBot(s, l.role);
            default -> {
            }
        }
        lfg.sendLfg(s.uuid);
    }

    private static void instance(GameServer server, PlayerSession s, C2S.InstanceAction i) {
        switch (i.action == null ? "" : i.action) {
            case "enter" -> {
                DungeonDef def = Dungeons.get(i.dungeon);
                Difficulty d = i.difficulty != null ? Difficulty.byName(i.difficulty) : null;
                if (def != null) server.instances.requestEnter(s, def, d != null ? d : def.difficulties.get(0));
            }
            case "start_key" -> server.instances.startKey(s);
            case "leave" -> server.instances.leave(s);
            case "release" -> server.instances.release(s);
            case "reset" -> server.instances.reset(s);
            default -> {
            }
        }
    }

    private static void pvp(GameServer server, PlayerSession s, C2S.PvpAction p) {
        switch (p.action == null ? "" : p.action) {
            case "queue" -> server.pvp.queue(s, p.bracket, p.rated);
            case "leave" -> {
                if (server.pvp.matchOfSession(s) != null) server.pvp.leaveMatch(s);
                else server.pvp.leaveQueue(s.uuid, true);
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ requests

    static void request(GameServer server, PlayerSession s, C2S.Request r) {
        switch (r.what == null ? "" : r.what) {
            case "vault" -> server.send(s, vault(s.profile));
            case "meter" -> server.send(s, meter(server, s, r.arg));
            case "leaderboard" -> server.send(s, leaderboard(server, r.arg));
            case "stats" -> server.send(s, Sync.stats(s.unit));
            case "character" -> s.characterDirty = true;
            case "lfg" -> server.lfg.sendLfg(s.uuid);
            case "group" -> server.groups.sendGroup(s.uuid);
            default -> {
            }
        }
    }

    static S2C.Vault vault(PlayerProfile p) {
        S2C.Vault v = new S2C.Vault();
        v.slots = GreatVault.slotLevels(p.vault);
        v.mythicRuns.addAll(p.vault.mythicRuns);
        v.raidKills.addAll(p.vault.raidKills);
        v.world = p.vault.worldActivities + p.vault.pvpWins;
        v.rewards.addAll(p.vault.pending);
        v.claimed = p.vault.claimed;
        return v;
    }

    static S2C.Meter meter(GameServer server, PlayerSession s, String which) {
        S2C.Meter m = new S2C.Meter();
        Group g = server.groups.groupOf(s.uuid);
        Meter meter = g != null ? g.meter : server.soloMeter(s);
        Meter.Segment seg = "overall".equals(which) ? meter.overall : meter.current.start >= 0 ? meter.current : meter.last != null ? meter.last : meter.current;
        double now = server.engine.now();
        m.duration = (float) seg.duration(now);
        m.segment = seg.label;
        for (Meter.Row row : seg.rows.values()) {
            S2C.MeterRow r = new S2C.MeterRow();
            r.name = row.name;
            r.color = row.color;
            r.damage = Math.round(row.damage);
            r.healing = Math.round(row.healing);
            r.damageTaken = Math.round(row.damageTaken);
            r.deaths = row.deaths;
            r.interrupts = row.interrupts;
            m.rows.add(r);
        }
        m.rows.sort((a, b) -> Double.compare(b.damage, a.damage));
        return m;
    }

    static S2C.Leaderboard leaderboard(GameServer server, String dungeon) {
        S2C.Leaderboard lb = new S2C.Leaderboard();
        WorldData w = server.worldData();
        List<String> ids = dungeon != null && w.leaderboard.containsKey(dungeon) ? List.of(dungeon) : new ArrayList<>(w.leaderboard.keySet());
        for (String id : ids) {
            List<WorldData.Run> runs = w.leaderboard.get(id);
            if (runs == null) continue;
            for (int i = 0; i < Math.min(dungeon != null ? 20 : 3, runs.size()); i++) {
                WorldData.Run run = runs.get(i);
                S2C.LeaderboardRow row = new S2C.LeaderboardRow();
                row.dungeon = run.dungeon;
                row.level = run.level;
                row.time = (float) run.time;
                row.timed = run.timed;
                row.party.addAll(run.party);
                lb.rows.add(row);
            }
        }
        List<java.util.Map.Entry<String, Double>> top = new ArrayList<>(w.ratings.entrySet());
        top.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        for (int i = 0; i < Math.min(10, top.size()); i++) {
            lb.topPlayers.add(w.names.getOrDefault(top.get(i).getKey(), "?"));
            lb.topRatings.add(top.get(i).getValue());
        }
        return lb;
    }

    private static void vaultClaim(GameServer server, PlayerSession s, C2S.VaultClaim v) {
        ItemData d = GreatVault.claim(s.profile, v.index);
        if (d == null) return;
        Rewards.giveLoot(server, s, List.of(d), L10n.of("Great Vault", "Великое хранилище"));
        server.send(s, vault(s.profile));
    }

    // ------------------------------------------------------------------ items

    private static void upgrade(GameServer server, PlayerSession s, C2S.Upgrade up) {
        EquipSlot slot = EquipSlot.byName(up.slot);
        if (slot == null) return;
        PlayerProfile p = s.profile;
        ItemData item;
        if (slot.isVanillaSlot()) {
            Equipment eq = server.platform.readEquipment(s.uuid);
            item = eq != null ? eq.get(slot) : null;
            if (item != null) item = item.copy();
        } else {
            item = p.extraSlots.get(slot.name());
        }
        if (item == null) return;
        ItemUpgrades.Result r = ItemUpgrades.upgrade(item, p);
        L10n text = switch (r) {
            case OK -> L10n.of("Upgraded to item level " + item.ilvl + ".", "Улучшено до уровня предмета " + item.ilvl + ".");
            case MAX_RANK -> L10n.of("Already at max rank.", "Уже максимальный ранг.");
            case NO_TRACK -> L10n.of("This item cannot be upgraded.", "Этот предмет нельзя улучшить.");
            case NOT_ENOUGH_VALORSTONES -> L10n.of("Not enough Valorstones.", "Недостаточно камней доблести.");
            case NOT_ENOUGH_CRESTS -> L10n.of("Not enough crests.", "Недостаточно гербов.");
        };
        server.msg(s, text, r == ItemUpgrades.Result.OK ? 0xFF40FF40 : 0xFFFF4040);
        if (r == ItemUpgrades.Result.OK && slot.isVanillaSlot()) server.platform.replaceEquipped(s.uuid, slot, item);
        server.applyCharacter(s);
    }

    private static void unequip(GameServer server, PlayerSession s, C2S.Unequip un) {
        EquipSlot slot = EquipSlot.byName(un.slot);
        if (slot == null || slot.isVanillaSlot()) return;
        if (s.unit.inCombat()) return;
        ItemData d = s.profile.extraSlots.remove(slot.name());
        if (d != null) {
            server.platform.giveItem(s.uuid, d);
            server.applyCharacter(s);
        }
    }

    private static void interact(GameServer server, PlayerSession s, C2S.Interact in) {
        S2C.OpenScreen o = new S2C.OpenScreen();
        o.screen = in.option;
        server.send(s, o);
    }

    private static void hello(GameServer server, PlayerSession s, C2S.Hello h) {
        s.lang = L10n.Lang.fromCode(h.language);
        s.characterDirty = true;
        server.groups.sendGroup(s.uuid);
        server.lfg.sendLfg(s.uuid);
        server.send(s, vault(s.profile));
        if (!s.profile.classChosen) {
            S2C.OpenScreen o = new S2C.OpenScreen();
            o.screen = "class_select";
            server.send(s, o);
        }
    }
}
