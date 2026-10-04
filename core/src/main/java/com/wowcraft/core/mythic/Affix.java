package com.wowcraft.core.mythic;

import com.wowcraft.core.util.L10n;

/** Mythic+ affixes. Implementation lives in {@link AffixHandler}. */
public enum Affix {
    FORTIFIED("Fortified", "Укрепленный", "Non-boss enemies have 20% more health and deal up to 30% more damage.",
            "У противников, не являющихся боссами, на 20% больше здоровья, и они наносят до 30% больше урона.", 0xFF7FB3D5),
    TYRANNICAL("Tyrannical", "Тиранический", "Bosses have 30% more health and deal up to 15% more damage.",
            "У боссов на 30% больше здоровья, и они наносят до 15% больше урона.", 0xFFE74C3C),
    BOLSTERING("Bolstering", "Усиливающий", "When a non-boss enemy dies, nearby enemies gain 20% health and damage.",
            "Когда погибает противник (не босс), союзники рядом с ним получают +20% к здоровью и урону.", 0xFFC0392B),
    RAGING("Raging", "Бушующий", "Non-boss enemies enrage at 30% health, dealing 50% more damage until defeated.",
            "Противники (не боссы) впадают в исступление при 30% здоровья и наносят на 50% больше урона.", 0xFFE67E22),
    SANGUINE("Sanguine", "Кровавый", "Slain non-boss enemies leave a pool of ichor that heals allies and damages players.",
            "Погибшие противники оставляют лужу ихора, исцеляющую их союзников и наносящую урон игрокам.", 0xFF922B21),
    BURSTING("Bursting", "Взрывной", "When slain, non-boss enemies explode, causing players to suffer damage over time.",
            "Погибая, противники взрываются, нанося всем игрокам периодический урон.", 0xFFF1948A),
    SPITEFUL("Spiteful", "Злобный", "Fiends rise from the corpses of non-boss enemies and pursue random players.",
            "Из трупов противников восстают злобные тени и преследуют случайных игроков.", 0xFF5B2C6F),
    VOLCANIC("Volcanic", "Вулканический", "While in combat, enemies periodically cause gouts of flame to erupt beneath distant players.",
            "Во время боя под игроками, стоящими вдали от противников, периодически извергается пламя.", 0xFFD35400),
    NECROTIC("Necrotic", "Некротический", "Enemy melee attacks apply a stacking blight that reduces healing received.",
            "Атаки противников в ближнем бою накладывают суммирующийся эффект, снижающий получаемое исцеление.", 0xFF1E8449),
    EXPLOSIVE("Explosive", "Взрывоопасный", "While in combat, enemies periodically summon Explosive Orbs that detonate if not destroyed.",
            "Во время боя противники призывают взрывоопасные сферы, которые нужно уничтожить.", 0xFF8E44AD),
    QUAKING("Quaking", "Сотрясающий", "Periodically, all players emit a shockwave, damaging and interrupting nearby allies.",
            "Время от времени игроки испускают ударную волну, наносящую урон и прерывающую союзников рядом.", 0xFF935116),
    GRIEVOUS("Grievous", "Мучительный", "When injured below 90% health, players suffer increasing damage over time.",
            "Пока здоровье игрока ниже 90%, он получает нарастающий периодический урон.", 0xFF641E16),
    STORMING("Storming", "Штормовой", "While in combat, enemies periodically summon damaging whirlwinds.",
            "Во время боя противники вызывают наносящие урон смерчи.", 0xFF5DADE2),
    ENTANGLING("Entangling", "Оплетающий", "Vines periodically sprout and ensnare players.",
            "Время от времени из земли вырастают лозы и опутывают игроков.", 0xFF27AE60),
    AFFLICTED("Afflicted", "Скорбящий", "Afflicted Souls appear; they must be dispelled or healed to full.",
            "Появляются страдающие души, которых нужно исцелить или очистить.", 0xFFA569BD),
    INCORPOREAL("Incorporeal", "Бесплотный", "Incorporeal Beings appear and must be crowd controlled or interrupted.",
            "Появляются бесплотные существа, которых нужно контролировать или прерывать.", 0xFF85929E),
    CHALLENGERS_PERIL("Challenger's Peril", "Опасность для претендента", "Dying subtracts 15 sec from the timer. Timer increased by 90 sec.",
            "Смерть отнимает 15 сек. от таймера. Таймер увеличен на 90 сек.", 0xFFF4D03F),
    VOID_PULSAR("Xal'atath's Bargain: Pulsar", "Сделка Ксал'атат: Пульсар", "Void orbs pulse periodically; stand inside them to absorb their energy.",
            "Периодически появляются сферы Бездны; встаньте в них, чтобы поглотить их энергию.", 0xFF6C3483),
    VOID_ASCENDANT("Xal'atath's Bargain: Ascendant", "Сделка Ксал'атат: Вознесение", "Void energy empowers enemies unless the orbs are destroyed.",
            "Энергия Бездны усиливает противников, если сферы не уничтожены.", 0xFF4A235A);

    public final L10n name;
    public final L10n description;
    public final int color;

    Affix(String en, String ru, String dEn, String dRu, int color) {
        this.name = L10n.of(en, ru);
        this.description = L10n.of(dEn, dRu);
        this.color = color;
    }

    public static Affix byName(String n) {
        for (Affix a : values()) if (a.name().equalsIgnoreCase(n)) return a;
        return null;
    }
}
