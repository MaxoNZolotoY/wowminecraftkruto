package com.wowcraft.core.talent;

import com.wowcraft.core.mod.Modifier;
import com.wowcraft.core.util.L10n;

import java.util.List;

/**
 * One talent. Effects: modifiers, a permanent passive aura and/or a granted ability.
 * Descriptions are generated from the effects unless a custom one is provided.
 */
public final class TalentNode {
    public final String id;
    public final L10n name;
    public final int row;
    public final int col;
    public final List<Modifier> mods;
    public final String passiveAura;
    public final String grantAbility;
    public final L10n customDescription;

    public TalentNode(String id, L10n name, int row, int col, List<Modifier> mods, String passiveAura,
                      String grantAbility, L10n customDescription) {
        this.id = id;
        this.name = name;
        this.row = row;
        this.col = col;
        this.mods = List.copyOf(mods);
        this.passiveAura = passiveAura;
        this.grantAbility = grantAbility;
        this.customDescription = customDescription;
    }

    public static Builder at(String id, int row, int col, String en, String ru) {
        return new Builder(id, row, col, L10n.of(en, ru));
    }

    public static final class Builder {
        private final String id;
        private final int row, col;
        private final L10n name;
        private final java.util.ArrayList<Modifier> mods = new java.util.ArrayList<>();
        private String aura, grant;
        private L10n desc;

        private Builder(String id, int row, int col, L10n name) {
            this.id = id;
            this.row = row;
            this.col = col;
            this.name = name;
        }

        public Builder mod(Modifier... m) {
            java.util.Collections.addAll(mods, m);
            return this;
        }

        public Builder aura(String auraId) {
            this.aura = auraId;
            return this;
        }

        public Builder grant(String abilityId) {
            this.grant = abilityId;
            return this;
        }

        public Builder desc(String en, String ru) {
            this.desc = L10n.of(en, ru);
            return this;
        }

        public TalentNode build() {
            return new TalentNode(id, name, row, col, mods, aura, grant, desc);
        }
    }
}
