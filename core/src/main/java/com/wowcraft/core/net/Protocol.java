package com.wowcraft.core.net;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wire format between server and client: [type id byte] + UTF-8 JSON of the message object.
 * Message classes are registered in a fixed order so ids match on both sides.
 */
public final class Protocol {
    public static final String CHANNEL = "wowcraft:main";
    public static final int VERSION = 1;

    private static final Gson GSON = new GsonBuilder().serializeSpecialFloatingPointValues().create();
    private static final List<Class<?>> TYPES = new ArrayList<>();
    private static final Map<Class<?>, Integer> IDS = new HashMap<>();

    static {
        for (Class<?> c : S2C.class.getDeclaredClasses()) register(c);
        for (Class<?> c : C2S.class.getDeclaredClasses()) register(c);
    }

    private Protocol() {
    }

    private static void register(Class<?> c) {
        IDS.put(c, TYPES.size());
        TYPES.add(c);
    }

    /** Stable ordering independent of reflection order. */
    static {
        TYPES.sort((a, b) -> a.getName().compareTo(b.getName()));
        IDS.clear();
        for (int i = 0; i < TYPES.size(); i++) IDS.put(TYPES.get(i), i);
    }

    public static byte[] encode(Object msg) {
        Integer id = IDS.get(msg.getClass());
        if (id == null) throw new IllegalArgumentException("Unregistered message " + msg.getClass());
        byte[] json = GSON.toJson(msg).getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[json.length + 2];
        out[0] = (byte) (id >> 8);
        out[1] = (byte) (id & 0xFF);
        System.arraycopy(json, 0, out, 2, json.length);
        return out;
    }

    public static Object decode(byte[] data) {
        if (data.length < 2) return null;
        int id = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
        if (id < 0 || id >= TYPES.size()) return null;
        String json = new String(data, 2, data.length - 2, StandardCharsets.UTF_8);
        return GSON.fromJson(json, TYPES.get(id));
    }

    public static Gson gson() {
        return GSON;
    }
}
