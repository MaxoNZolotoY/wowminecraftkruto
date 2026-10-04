# Расширение мода: модели, анимации, контент

## 1. Модели и анимации (место для них оставлено)

Каждое тело NPC/бота/питомца — сущность `WowNpcEntity`, которая синхронизирует на клиент:

| Поле | Откуда | Для чего |
|------|--------|----------|
| `TEMPLATE` | id шаблона NPC | выбор модели / текстуры |
| `TEXTURE` | `NpcTemplate.texture` | ключ заглушки (zombie, husk, skeleton, spider, blaze…) |
| `MODEL` | `NpcTemplate.modelId` (`.model(...)`) | id вашей модели, напр. `wowcraft:npc/korgath` |
| `ANIMATION` | `NpcTemplate.animationSet` | набор анимаций, напр. `wowcraft:humanoid` |
| `TINT`, `SCALE` | шаблон | окраска и масштаб заглушки |
| `DEAD` | ядро | поза смерти ботов |

### Только текстура
Положите PNG в ресурс-пак или в мод: `assets/wowcraft/textures/entity/npc/<id_шаблона>.png` (раскладка 64×64 как у скина
игрока) — подхватится автоматически вместо заглушки. Иконки способностей/аур:
`assets/wowcraft/textures/gui/ability/<id>.png`, `.../gui/aura/<id>.png`.

### Свои модели (GeckoLib)
1. Добавьте зависимость GeckoLib 4 для 1.20.1 в `fabric/build.gradle`.
2. Создайте `GeoModel<WowNpcEntity>`, который берёт `modelId()`/`animationSet()` у сущности и возвращает
   `geo/<model>.geo.json`, `animations/<set>.animation.json`, текстуру.
3. Сделайте `WowNpcEntity` реализацией `GeoEntity` (контроллер: idle / walk по скорости, `attack` при взмахе,
   `cast` пока `unit.isCasting()`, `death` при `DEAD`). Анимации способностей: ядро вызывает `Body.playAnimation(key)`
   (ключ = `Ability.animation`) — пробросьте его в триггер анимации.
4. В `WowCraftClient` зарегистрируйте `GeoEntityRenderer` для нужных типов (или выбирайте рендерер по `modelId`,
   если он не пустой, иначе заглушку из `NpcRenderers`).

Визуальные эффекты способностей приходят сообщением `S2C.Vfx` с ключом (`Ability.vfx`) — сейчас это частицы
(`WorldFx.vfx`), туда же можно подключить свои эффекты/модели снарядов.

## 2. Новое подземелье
Скопируйте любой класс из `core/.../content/pve/` (например `GrimholdDepths`) и зарегистрируйте его в `Content.bootstrap()`.
DSL `NpcKit`:
```java
strike("id", "Name", "Имя", School.PHYSICAL, 1.8);               // удар ближнего боя (в ударах моба)
bolt("id", "Name", "Имя", School.FIRE, 2.2, 2.5);                 // прерываемый каст
cleave(...), nova(...), mend(...), afflict(...), groundZone(...)  // фронталки, АоЕ, лечение, дебафы, лужи
npc("id", "Name", "Имя", NpcRank.ELITE, BodyType.HUMANOID, "husk", 0xFF9A7B5A).spell(sp("id", cd, delay));
boss("id", ..., "script_id").hp(0.6);
BossScripts.register("script_id", MyBoss::new);
dungeon(ID, "SHORT", "Name", "Имя", "desc", "описание", "stone", 25 * 60, RoomDef.entrance(...), RoomDef.trash(...), RoomDef.boss(...));
item(ID, "item_id", ...);                                          // именная добыча
```
Скрипт босса наследует `BossScript`: `every(...)`, `after(...)`, `circleAt/circleOn/coneAt/lineTo/ringAround`,
`stackOn`, `soakAt`, `poolAt`, `addsAround`, `shieldBoss`, `enrageAfter`, `thresholds()/onHealthBelow()` для фаз.
Проверьте тестами: `PveContentTest` (целостность) и симуляцию в `GameServerTest`.

## 3. Способности и классы
Классы — `core/.../content/classes/*Content.java`: способности (`Ability.builder`), ауры (`AuraDef`), таланты
(`TalentNode`), панель по умолчанию и **ротация бота** (`Rotation`). Тест `ContentTest.everySpecRunsItsRotation`
прогоняет все 39 спеков.
