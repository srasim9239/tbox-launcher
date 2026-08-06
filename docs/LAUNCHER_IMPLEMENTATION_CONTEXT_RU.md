# Контекст реализации TBox Home

Этот файл хранит проверенные детали лаунчера, чтобы следующие доработки не
повторяли разведку OEM API.

## Кузов и двери (Android 9 / mbCAN)

- Основной источник: `MBCanEngine`,
  `eMBCAN_VEHICLE_BCM_STATUS` (21).
- Push API: `registCarDorListener(IMbCanVehicleDoorCallback)`.
- Callback и fallback polling читают BCM status (21). Тип 5 не использовать:
  на этой прошивке его снимок менее надёжен.
- Проверено на ГУ `10.218.232.115` 26.07.2026:
  - `1` = закрыто;
  - `2` = открыто;
  - в момент проверки была открыта передняя пассажирская дверь:
    `driver=1, passenger=2, rearLeft=1, rearRight=1, trunk=1, hood=1`.
- Не использовать проверку `raw != 0`: она помечает все закрытые двери как
  открытые.
- Значки кузова показываются только около 3D-модели и только при `raw == 2`.
  В обычном состоянии значков быть не должно.

## Нижняя панель

- Центральная группа климата должна быть привязана к геометрическому центру
  экрана через `Box` + `Modifier.align(Alignment.Center)`, а не `Row.weight`.
- Боковые группы выравниваются `CenterStart` / `CenterEnd` и не должны сдвигать
  климат.
- Сиденье: raw `1=off`, `2..4=heat 1..3`, `5..7=vent 1..3`.
  UI использует optimistic raw state, чтобы быстрые повторные нажатия не
  отправляли одно и то же значение до следующего CAN polling.
- `LauncherBottomBar` обязан держать source-interest для AC, AUTO,
  рециркуляции, обоих defrost, обогрева руля и property 138/139. Иначе
  mbCAN/VHAL push и fallback poll не активируются, а внешние OEM/voice
  изменения не доходят до UI.
- Вентиляция рисуется слоями `ic_widget_seat_vent_0..3`: уровень 1 включает
  первую лопасть, уровень 2 — две, уровень 3 — три.
- Задний ПТФ: raw `1=off`, `2=on`; отправлять явное значение, а не повторно
  читать устаревшее состояние перед каждым быстрым нажатием.
- Физический цикл света — property `135` (`eVEHICLE_LIGHTCONTROL`):
  `4=Off → 2=Габариты → 3=Ближний → 1=Auto → 4`.
- Property `19` — бинарный HMA/IHBC OEM-настройки, это не цикл фар.

## Настройки авто

- Реализация: Compose overlay внутри `TeslaLauncherScreen`, без WindowManager
  reveal-анимации.
- Настройки занимают 55% ширины, модель — 45%. Compose-scale всего SceneView
  запрещён; размер задаётся камерой и `SETTINGS_MODEL_SCALE=0.40`.
- Home-модель использует `HOME_MODEL_SCALE=0.52`, yaw `0°`; settings-модель
  центрируется без искусственного X/Z offset.
- Родитель карточки секции не должен перехватывать клики дочерних переключателей:
  кликабелен только заголовок.
- Минимальная активная область строки управления: 52–68 dp.
- Режим фар выбирается напрямую одной из четырёх крупных кнопок через property
  `135`: `4=Off`, `2=Position`, `3=Low`, `1=Auto`.

## App drawer и медиаплеер

- App drawer живёт в focusable `TYPE_APPLICATION_OVERLAY`, поэтому остаётся
  выше freeform-приложений. Перед запуском приложения окно обязательно
  удаляется через `removeViewImmediate`.
- Root `ComposeView` перехватывает `KEYCODE_BACK` на down/up, иначе после
  удаления overlay тот же Back может попасть в HOME и поднять чужую задачу.
- Drag-to-grid в system overlay отключён: координаты overlay и HOME относятся
  к разным окнам. Скрытие, сортировка, edit mode и pin-to-first-slot сохранены.
- Play мини-плеера использует `launchAppIfNeeded=true` и
  `keepPlayerForeground=true`; обложка и название открывают привязанный пакет.

## Runtime rig и TPMS

- Обязательные pivots:
  `p_door01..04`, `houbeimen_copy`,
  `wheel_lungu01/02_L/R`. Gradle task `validateLauncherCarRigAssets`
  проверяет GLB и pivot JSON перед любой сборкой.
- Двери вращаются на ±65°, багажник на 60° по Z. Первый frame после recreation
  сразу принимает фактическое состояние, последующие изменения сглаживаются
  примерно за 250–400 мс.
- Скорость вне `0..300 км/ч`, steering вне `-1080..1080°`, NaN/Infinity
  отбрасываются до rig. Поворот front wheel pivot временно отключён: экспорт
  узла захватывает соседнюю геометрию подкрылка; wheel spin и TPMS anchors
  сохранены.
- Wheel anchors публикуются не чаще 10 Гц из world transforms Filament.
  TPMS-сноски ограничиваются панелью, разводятся при коллизии и рисуют линию к
  pivot; invalid/calibrating значения не выводятся.
- Люк статичен: подтверждённого production CAN/VHAL сигнала нет.

## Проверка перед эксплуатацией

1. `compileRuDebugKotlin`.
2. Полный `testRuDebugUnitTest`; Theme-тесты используют
   `kotlin.io.path.createTempDirectory` и больше не блокируют Kotlin compiler.
3. `assembleRuDebug`.
4. Установка через ADB.
5. Скриншот 1920×1080: проверить геометрический центр климата и отсутствие
   значков закрытых дверей.
6. Сравнить открытые двери со штатным `com.wt.launcher3`.

### Hardware QA 26.07.2026 (`10.218.232.115:5555`)

- `testRuDebugUnitTest` и `assembleRuDebug` успешны; APK установлена.
- Drawer визуально подтверждён выше запущенной Я.Музыки; физический Back
  закрывает только overlay после отдельного root key interceptor.
- Cold-start Я.Музыки кнопкой Play подтверждён через resumed Activity.
- Настройки закрываются физическим Back, модель и открытые части кузова
  рендерятся; missing-pivot и TBox crash/ANR в logcat отсутствуют.
- На момент проверки TPMS не отдавал валидные pressure/temperature, поэтому
  сноски корректно скрыты. Проверка wheel spin на движущемся автомобиле и
  внешних voice-toggle климата требует физического заезда/голосовой команды.
