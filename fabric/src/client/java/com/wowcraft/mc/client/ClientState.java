package com.wowcraft.mc.client;

import com.wowcraft.core.net.S2C;
import com.wowcraft.core.spec.Spec;
import com.wowcraft.core.util.L10n;
import com.wowcraft.mc.client.screen.ClassSelectScreen;
import com.wowcraft.mc.client.screen.CharacterScreen;
import com.wowcraft.mc.client.screen.GroupFinderScreen;
import com.wowcraft.mc.client.screen.TalentScreen;
import com.wowcraft.mc.client.screen.VaultScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Everything the client knows about the WoW game, updated from server messages. */
public final class ClientState {
    private ClientState() {
    }

    public static S2C.Character character = new S2C.Character();
    public static S2C.Self self;
    public static final Map<Integer, S2C.Unit> units = new LinkedHashMap<>();
    public static List<Integer> party = new ArrayList<>();
    public static List<Integer> bosses = new ArrayList<>();
    public static S2C.InstanceStatus instance;
    public static S2C.PvpStatus pvp;
    public static S2C.Group group = new S2C.Group();
    public static S2C.Lfg lfg = new S2C.Lfg();
    public static S2C.Vault vault;
    public static S2C.Meter meter;
    public static S2C.Leaderboard leaderboard;
    public static S2C.Stats stats;
    public static S2C.Invite invite;
    public static long inviteAt;
    public static final Map<Integer, Timed<S2C.Telegraph>> telegraphs = new HashMap<>();
    public static final Map<Integer, Timed<S2C.Area>> areas = new HashMap<>();
    public static S2C.BossTimers timers = new S2C.BossTimers();
    public static long timersAt;
    public static final Deque<FloatingText> combatText = new ArrayDeque<>();
    public static final Deque<Timed<S2C.Warning>> warnings = new ArrayDeque<>();
    public static final Deque<Timed<S2C.Loot>> loot = new ArrayDeque<>();
    public static final Deque<Timed<S2C.Error>> errors = new ArrayDeque<>();
    public static long selfAt;
    public static boolean helloSent;
    /** Message counts by type (diagnostics, client smoke test). */
    public static final Map<String, Integer> received = new java.util.concurrent.ConcurrentHashMap<>();

    /** A message with the time it arrived (ms). */
    public static final class Timed<T> {
        public final T value;
        public final long at;

        Timed(T value) {
            this.value = value;
            this.at = System.currentTimeMillis();
        }

        public float age() {
            return (System.currentTimeMillis() - at) / 1000f;
        }
    }

    public static final class FloatingText {
        public final S2C.CombatText e;
        public final long at = System.currentTimeMillis();
        public final double jitterX, jitterZ;

        FloatingText(S2C.CombatText e) {
            this.e = e;
            this.jitterX = (Math.random() - 0.5) * 0.8;
            this.jitterZ = (Math.random() - 0.5) * 0.8;
        }

        public float age() {
            return (System.currentTimeMillis() - at) / 1000f;
        }
    }

    public static L10n.Lang lang() {
        MinecraftClient mc = MinecraftClient.getInstance();
        String code = mc != null && mc.getLanguageManager() != null ? mc.getLanguageManager().getLanguage() : "en_us";
        return L10n.Lang.fromCode(code);
    }

    public static String t(S2C.Text text) {
        if (text == null) return "";
        return lang() == L10n.Lang.RU && text.ru != null ? text.ru : text.en;
    }

    public static String t(L10n text) {
        return text == null ? "" : text.get(lang());
    }

    public static String t(String en, String ru) {
        return lang() == L10n.Lang.RU ? ru : en;
    }

    public static Spec spec() {
        return character != null && character.spec != null ? Spec.byId(character.spec) : null;
    }

    public static S2C.Unit unit(int id) {
        return units.get(id);
    }

    public static S2C.Unit target() {
        return self != null && self.targetId >= 0 ? units.get(self.targetId) : null;
    }

    public static void reset() {
        helloSent = false;
        character = new S2C.Character();
        self = null;
        units.clear();
        party = new ArrayList<>();
        bosses = new ArrayList<>();
        instance = null;
        pvp = null;
        group = new S2C.Group();
        telegraphs.clear();
        areas.clear();
        combatText.clear();
        warnings.clear();
        timers = new S2C.BossTimers();
    }

    public static void handle(Object msg) {
        MinecraftClient mc = MinecraftClient.getInstance();
        received.merge(msg.getClass().getSimpleName(), 1, Integer::sum);
        if (msg instanceof S2C.Self s) {
            self = s;
            selfAt = System.currentTimeMillis();
        } else if (msg instanceof S2C.Units u) {
            units.clear();
            for (S2C.Unit unit : u.units) units.put(unit.entityId, unit);
            party = u.party;
            bosses = u.bosses;
        } else if (msg instanceof S2C.Character c) {
            character = c;
        } else if (msg instanceof S2C.CombatTextBatch b) {
            for (S2C.CombatText e : b.events) {
                combatText.add(new FloatingText(e));
                while (combatText.size() > 60) combatText.poll();
            }
        } else if (msg instanceof S2C.Error e) {
            if (e.code != null && !e.code.equals("GCD") && !e.code.equals("QUEUED") && !e.code.equals("OK")) {
                errors.add(new Timed<>(e));
                while (errors.size() > 3) errors.poll();
            }
        } else if (msg instanceof S2C.Vfx v) {
            com.wowcraft.mc.client.render.WorldFx.vfx(v);
        } else if (msg instanceof S2C.Telegraph t) {
            if (t.remove) telegraphs.remove(t.id);
            else telegraphs.put(t.id, new Timed<>(t));
        } else if (msg instanceof S2C.Area a) {
            if (a.remove) areas.remove(a.id);
            else areas.put(a.id, new Timed<>(a));
        } else if (msg instanceof S2C.BossTimers t) {
            timers = t;
            timersAt = System.currentTimeMillis();
        } else if (msg instanceof S2C.Warning w) {
            warnings.add(new Timed<>(w));
            while (warnings.size() > 3) warnings.poll();
            if (mc.player != null && w.sound != null) {
                mc.player.playSound(net.minecraft.sound.SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 0.6f);
            }
        } else if (msg instanceof S2C.Chat c) {
            if (mc.player != null) mc.player.sendMessage(Text.literal(t(c.text)).styled(st -> st.withColor(c.color & 0xFFFFFF)), false);
        } else if (msg instanceof S2C.InstanceStatus s) {
            instance = s.active ? s : null;
            if (!s.active) {
                telegraphs.clear();
                timers = new S2C.BossTimers();
            }
        } else if (msg instanceof S2C.Group g) {
            group = g;
        } else if (msg instanceof S2C.Invite i) {
            invite = i;
            inviteAt = System.currentTimeMillis();
        } else if (msg instanceof S2C.Lfg l) {
            lfg = l;
        } else if (msg instanceof S2C.PvpStatus p) {
            pvp = p.active ? p : null;
        } else if (msg instanceof S2C.Loot l) {
            loot.add(new Timed<>(l));
            while (loot.size() > 4) loot.poll();
        } else if (msg instanceof S2C.Vault v) {
            vault = v;
        } else if (msg instanceof S2C.Meter m) {
            meter = m;
        } else if (msg instanceof S2C.Leaderboard l) {
            leaderboard = l;
        } else if (msg instanceof S2C.Stats s) {
            stats = s;
        } else if (msg instanceof S2C.OpenScreen o) {
            open(o.screen);
        }
    }

    public static void open(String screen) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (screen == null) return;
        switch (screen) {
            case "class_select" -> mc.setScreen(new ClassSelectScreen());
            case "talents" -> mc.setScreen(new TalentScreen());
            case "character" -> mc.setScreen(new CharacterScreen());
            case "group_finder", "lfg", "pvp" -> mc.setScreen(new GroupFinderScreen());
            case "vault" -> mc.setScreen(new VaultScreen());
            default -> {
            }
        }
    }

    /** Drops expired transient entries. */
    public static void cleanup() {
        long now = System.currentTimeMillis();
        combatText.removeIf(f -> f.age() > 1.6f);
        warnings.removeIf(w -> w.age() > 4f);
        loot.removeIf(l -> l.age() > 7f);
        errors.removeIf(e -> e.age() > 2f);
        for (Iterator<Timed<S2C.Telegraph>> it = telegraphs.values().iterator(); it.hasNext(); ) {
            Timed<S2C.Telegraph> t = it.next();
            if (t.age() > t.value.duration + 1.0f) it.remove();
        }
        for (Iterator<Timed<S2C.Area>> it = areas.values().iterator(); it.hasNext(); ) {
            Timed<S2C.Area> a = it.next();
            if (a.age() > a.value.duration + 1.0f) it.remove();
        }
        if (invite != null && now - inviteAt > 60_000) invite = null;
    }
}
