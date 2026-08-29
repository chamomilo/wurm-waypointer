# Waypointer API для внешних client-модов

Короткий контекст, который можно целиком скопировать в новую задачу Codex.

## Правило интеграции

Не обращаться к `org.waypoints.next.*`, private-полям или внутренним
`SurroundingKind`/контроллерам. Единственная публичная точка входа:

```text
org.waypoints.api.WaypointerApi
```

Интеграция всегда необязательная и fail-open:

1. Через `Class.forName` найти `WaypointerApi`.
2. Вызвать статический `isInstalled()`; продолжать только при `true`.
3. При необходимости проверить `apiVersion()` (текущая версия — `1`) и
   `capabilities()` (`OBJECT_MARKS`, `SUBJECT_LIFECYCLE`, `NAVIGATION`).
4. Если API отсутствует, вернул `false` или вызов завершился ошибкой, внешний
   мод продолжает работать без функций Waypointer.

## Простые reflection-команды

Создать/обновить 15-минутный маркер на загруженном объекте:

```java
Class<?> api = Class.forName("org.waypoints.api.WaypointerApi");
boolean installed = Boolean.TRUE.equals(
        api.getMethod("isInstalled").invoke(null));
if (installed) {
    boolean marked = Boolean.TRUE.equals(api.getMethod("markObject",
            String.class, String.class, String.class, long.class,
            String.class, boolean.class).invoke(null,
            "my.mod.id",          // стабильный ID мода-владельца
            "selected:" + wurmId, // idempotency key маркера
            "AUTO",               // AUTO | CREATURE | ITEM | CONTAINER
            wurmId,
            "ALERT",              // ALERT | TARGET | BEAM | COMPASS_ONLY
            true));                // true: также включить/перенести NAV
}
```

Сообщить, что исходный объект исчез (смерть — это исчезновение creature;
corpse является новым item и маркер на него не переносится):

```java
api.getMethod("subjectVanished", String.class, long.class)
        .invoke(null, "CREATURE", killedCreatureId);
```

Typed API дополнительно предоставляет `ObjectMarkRequest`, `MarkResult`,
`WurmObjectRef`, `removeOwnedMarker(...)` и `setNavigation(...)`. Маркер
удаляется Waypointer при исчезновении объекта; при удалении активного маркера
NAV останавливается автоматически.

## Задание для другой задачи

> Интегрируй этот client-мод с Waypointer только через публичный
> `org.waypoints.api.WaypointerApi` по правилам из этого файла. Сначала выполни
> optional handshake `isInstalled()`, не создавай жёсткую runtime-зависимость,
> используй уникальные `ownerId` и `markerKey`, а события удаления/замены
> объекта передавай через `subjectVanished`. Интеграция должна быть fail-open.
