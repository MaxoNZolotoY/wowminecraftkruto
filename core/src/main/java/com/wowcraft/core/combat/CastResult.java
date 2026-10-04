package com.wowcraft.core.combat;

import com.wowcraft.core.util.L10n;

public enum CastResult {
    OK("", ""),
    QUEUED("", ""),
    UNKNOWN("You don't know that ability", "Вы не знаете эту способность"),
    DEAD("You are dead", "Вы мертвы"),
    CROWD_CONTROLLED("You can't do that right now", "Сейчас вы не можете этого сделать"),
    SILENCED("Can't do that while silenced", "Невозможно под действием немоты"),
    LOCKED_OUT("That school is locked", "Эта школа магии заблокирована"),
    COOLDOWN("Ability is not ready yet", "Способность еще не готова"),
    GCD("", ""),
    BUSY("Another action is in progress", "Вы заняты другим действием"),
    NO_TARGET("You have no target", "Нет цели"),
    INVALID_TARGET("Invalid target", "Неверная цель"),
    OUT_OF_RANGE("Out of range", "Слишком далеко"),
    TOO_CLOSE("Target is too close", "Цель слишком близко"),
    NO_LINE_OF_SIGHT("Target not in line of sight", "Цель вне поля зрения"),
    NOT_FACING("You must be facing your target", "Нужно стоять лицом к цели"),
    NO_RESOURCE("Not enough resources", "Недостаточно ресурсов"),
    REQUIREMENT("Requirements not met", "Условия не выполнены"),
    MOVING("Can't do that while moving", "Невозможно во время движения"),
    PASSIVE("That ability is passive", "Это пассивная способность"),
    WRONG_SPEC("Wrong specialization", "Неподходящая специализация");

    public final L10n message;

    CastResult(String en, String ru) {
        this.message = L10n.of(en, ru);
    }

    public boolean ok() {
        return this == OK || this == QUEUED;
    }
}
