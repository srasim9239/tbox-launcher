# Настройка TBox Launcher на ГУ (adb-разрешения и устройства)

## Устройства

| Устройство | Адрес adb | Примечание |
|------------|-----------|------------|
| Стендовое ГУ | `192.168.1.128:5555` | адрес постоянный |
| Автомобильное ГУ | меняется (`192.168.42.167`, `10.91.32.115`, ...) | уточнять адрес перед подключением |

Подключение: `adb connect <адрес>:5555`

## Разрешения (выдаются один раз после установки APK)

Пакет: `ras.dashing.tbox.launcher`

```bash
PKG=ras.dashing.tbox.launcher

# Свободные окна (freeform): границы запуска, immersive-режим
adb shell pm grant $PKG android.permission.WRITE_SECURE_SETTINGS
adb shell appops set $PKG SYSTEM_ALERT_WINDOW allow          # оверлеи поверх freeform (настройки, быстрый запуск, «О лаунчере»)
adb shell settings put global policy_control 'immersive.full=*'

# Управление стеками задач: кнопки Home/Back, запуск/скрытие freeform-приложений
adb shell pm grant $PKG android.permission.MANAGE_ACTIVITY_STACKS
adb shell pm grant $PKG android.permission.ACTIVITY_EMBEDDING

# Логи для чтения параметров Wi-Fi SoftAP (QR в настройках авто)
adb shell pm grant $PKG android.permission.READ_LOGS

# GET_TASKS выдаётся автоматически; REAL_GET_TASKS этой прошивкой не выдаётся (нормально)
```

### Сервис специальных возможностей (кнопка «Назад»)

Кнопка «назад» в доке шлёт системный back через accessibility-сервис.
ВАЖНО: не затирать уже включённые сервисы — сначала читаем список:

```bash
adb shell settings get secure enabled_accessibility_services
# дописываем наш сервис через двоеточие к существующему значению:
adb shell settings put secure enabled_accessibility_services '<существующие>:ras.dashing.tbox.launcher/vad.dashing.tbox.ui.launcher.LauncherNavAccessibilityService'
adb shell settings put secure accessibility_enabled 1
```

### Прочее (выдаётся из UI)

- **Доступ к уведомлениям** (виджет плеера): настройки авто → кнопка доступа к уведомлениям, либо
  `settings put secure enabled_notification_listeners` (дописать через двоеточие).
- **Установка неизвестных приложений** (OTA-обновления): разрешить в настройках при первом обновлении,
  либо `adb shell appops set $PKG REQUEST_INSTALL_PACKAGES allow`.

### Лаунчер домашним по умолчанию

См. `DEFAULT_HOME_RU.md` (отключение штатного лаунчера с инструкцией отката).

## Проверка

```bash
adb shell dumpsys package ras.dashing.tbox.launcher | grep granted=true
adb shell appops get ras.dashing.tbox.launcher SYSTEM_ALERT_WINDOW
adb shell settings get secure enabled_accessibility_services
```
