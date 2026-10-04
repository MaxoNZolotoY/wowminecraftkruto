package com.wowcraft.core.content;

import com.wowcraft.core.content.classes.ClassContent;
import com.wowcraft.core.content.classes.WarriorContent;
import com.wowcraft.core.spec.WowClass;

import java.util.EnumMap;
import java.util.Map;

/** Registers all game content exactly once. */
public final class Content {
    private static boolean bootstrapped;
    private static final Map<WowClass, ClassKit> KITS = new EnumMap<>(WowClass.class);

    private Content() {
    }

    public static synchronized void bootstrap() {
        if (bootstrapped) return;
        bootstrapped = true;
        CommonContent.register();
        for (ClassContent c : classes()) {
            c.register();
            KITS.put(c.kit().wowClass, c.kit());
        }
        ItemContent.register();
        PveContent.register();
    }

    private static ClassContent[] classes() {
        return new ClassContent[]{
                new WarriorContent(),
                new com.wowcraft.core.content.classes.PaladinContent(),
                new com.wowcraft.core.content.classes.HunterContent(),
                new com.wowcraft.core.content.classes.RogueContent(),
                new com.wowcraft.core.content.classes.PriestContent(),
                new com.wowcraft.core.content.classes.DeathKnightContent(),
                new com.wowcraft.core.content.classes.ShamanContent(),
                new com.wowcraft.core.content.classes.MageContent(),
                new com.wowcraft.core.content.classes.WarlockContent(),
                new com.wowcraft.core.content.classes.MonkContent(),
                new com.wowcraft.core.content.classes.DruidContent(),
                new com.wowcraft.core.content.classes.DemonHunterContent(),
                new com.wowcraft.core.content.classes.EvokerContent(),
        };
    }

    public static ClassKit kit(WowClass c) {
        bootstrap();
        return KITS.get(c);
    }

    public static Map<WowClass, ClassKit> kits() {
        bootstrap();
        return KITS;
    }
}
