# TBox Launcher — права через ADB AppControl / Bugjaeger

Пакет: **`ras.dashing.tbox.launcher`**

Подходит для приложений на телефоне/планшете:
- **ADB AppControl**
- **Bugjaeger**

Подключитесь к ГУ (USB или Wi‑Fi), откройте **Shell / Console** и вставьте блок целиком.

> Если команды выполняете с ПК через `adb`, перед каждой строкой добавьте `adb shell `, либо так:  
> `adb shell "команда1; команда2; …"`.

---

## Одна команда (все нужные права)

Скопируйте и выполните в Shell:

```
pm grant ras.dashing.tbox.launcher android.permission.WRITE_SECURE_SETTINGS; pm grant ras.dashing.tbox.launcher android.permission.MANAGE_ACTIVITY_STACKS; pm grant ras.dashing.tbox.launcher android.permission.ACTIVITY_EMBEDDING; pm grant ras.dashing.tbox.launcher android.permission.READ_LOGS; appops set ras.dashing.tbox.launcher SYSTEM_ALERT_WINDOW allow; appops set ras.dashing.tbox.launcher REQUEST_INSTALL_PACKAGES allow; settings put global enable_freeform_support 1; settings put global policy_control immersive.full=*; settings put secure enabled_accessibility_services ras.dashing.tbox.launcher/vad.dashing.tbox.ui.launcher.LauncherNavAccessibilityService; settings put secure accessibility_enabled 1
```

### Что это даёт

| Часть команды | Зачем |
|---------------|--------|
| `WRITE_SECURE_SETTINGS` | Freeform, immersive, авто-включение сервиса «Назад» |
| `MANAGE_ACTIVITY_STACKS` | Home закрывает freeform, управление стеком задач |
| `ACTIVITY_EMBEDDING` | Запуск приложений в freeform-окнах |
| `READ_LOGS` | QR Wi‑Fi SoftAP в настройках авто |
| `SYSTEM_ALERT_WINDOW allow` | Drawer / настройки / about поверх freeform |
| `REQUEST_INSTALL_PACKAGES allow` | OTA-обновления из лаунчера |
| `enable_freeform_support 1` | Включить поддержку freeform на ГУ |
| `policy_control immersive.full=*` | Полноэкранный immersive (без лишних системных панелей) |
| `enabled_accessibility_services` + `accessibility_enabled 1` | Кнопка «Назад» в доке (сервис спец. возможностей) |

`INJECT_EVENTS` через эти приложения **не выдаётся** (только системная подпись) — для лаунчера это нормально.

---

## Опционально: отключить родной лаунчер

Чтобы не было диалога выбора HOME и сразу грузился TBox Launcher:

```
pm disable-user --user 0 com.wt.launcher3
```

Проверка:

```
cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
```

Ожидаемо: `ras.dashing.tbox.launcher/vad.dashing.tbox.LauncherHomeActivity`

При желании перезагрузите ГУ: `reboot`

### Вернуть родной лаунчер

```
pm enable com.wt.launcher3
```

Данные штатного лаунчера не удаляются. После включения снова может появиться выбор HOME.

**Важно:** не удаляйте TBox Launcher, пока `com.wt.launcher3` отключён. Если остались без лаунчера — выполните `pm enable com.wt.launcher3`.

---

## Проверка прав

```
dumpsys package ras.dashing.tbox.launcher | grep granted=true; appops get ras.dashing.tbox.launcher SYSTEM_ALERT_WINDOW; settings get secure enabled_accessibility_services
```

---

## Откат прав (по желанию)

```
pm revoke ras.dashing.tbox.launcher android.permission.WRITE_SECURE_SETTINGS; pm revoke ras.dashing.tbox.launcher android.permission.MANAGE_ACTIVITY_STACKS; pm revoke ras.dashing.tbox.launcher android.permission.ACTIVITY_EMBEDDING; pm revoke ras.dashing.tbox.launcher android.permission.READ_LOGS; appops set ras.dashing.tbox.launcher SYSTEM_ALERT_WINDOW deny; appops set ras.dashing.tbox.launcher REQUEST_INSTALL_PACKAGES deny; settings put secure accessibility_enabled 0
```
