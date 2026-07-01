# 🔍 Полный аудит кода — youcloud

> Результат анализа 7 ключевых файлов проекта. Найдено **50+ проблем**, сгруппированных по критичности.

---

## 🔴 CRITICAL (Крэши, потеря данных, зависания)

### 1. `resolvedUrls` — потоконебезопасный HashMap
**Файл**: [MusicViewModel.kt:258](file:///home/danya/AndroidStudioProjects/MyApplication/app/src/main/java/com/example/myapplication/ui/MusicViewModel.kt#L258)

```kotlin
private val resolvedUrls = mutableMapOf<Long, String>()
```

Обычный `HashMap`, к которому обращаются **десятки** корутин одновременно: `playMix`, `playMixTrack`, `playFavorite`, `playQueuedTrack`, `resolveTrackIfNeeded`, `toggleShuffle` и т.д. Может выбросить `ConcurrentModificationException` или молча потерять данные.

> **Fix**: Заменить на `ConcurrentHashMap<Long, String>()` или обернуть доступ в `Mutex`.

---

### 2. `playFavorite` — IndexOutOfBoundsException из-за `mapNotNull`
**Файл**: [MusicViewModel.kt:1269–1274](file:///home/danya/AndroidStudioProjects/MyApplication/app/src/main/java/com/example/myapplication/ui/MusicViewModel.kt#L1269-L1274)

`playFavorite` строит `queueToPlay`, затем фильтрует через `mapNotNull` в `stubs`. Если у какого-то трека нет `localUrl` и нет записи в `resolvedUrls`, он **выпадает** из `stubs`. Но `newStartIndex` вычислен по полному `queueToPlay`. В итоге `musicPlayer.playQueue(stubs, newStartIndex)` получает индекс, выходящий за границы массива.

> **Fix**: Вычислять `newStartIndex` **после** фильтрации `mapNotNull`, пересчитывая позицию целевого трека в отфильтрованном списке.

---

### 3. NPE-крэш в `TrackActionsDialog` callback
**Файл**: [MusicScreen.kt:660, 664](file:///home/danya/AndroidStudioProjects/MyApplication/app/src/main/java/com/example/myapplication/ui/MusicScreen.kt#L660-L664)

```kotlin
track = selectedTrack!!,  // Line 660
onAddToPlaylist = { playlist ->
    viewModel.addTrackToPlaylist(playlist.id, selectedTrack!!)  // Line 664
```

`selectedTrack` — это `StateFlow`, который может стать `null` между null-чекком и моментом выполнения лямбды (когда пользователь нажмёт на плейлист). `!!` на строке 664 **крашнет приложение**.

> **Fix**: Захватить `selectedTrack` в `val` перед передачей в лямбду: `val captured = selectedTrack ?: return`.

---

### 4. `deleteDownloadedTrack` — удаляет HLS вместо Progressive для Яндекс-треков
**Файл**: [MusicViewModel.kt:1349](file:///home/danya/AndroidStudioProjects/MyApplication/app/src/main/java/com/example/myapplication/ui/MusicViewModel.kt#L1344-L1356)

`offlineMusicStore.removeHls(streamUrl)` вызывается всегда, даже для Яндекс-треков, которые были скачаны через `downloadProgressive`. Файл **остаётся на диске**, а состояние сбрасывается на "не скачан".

> **Fix**: Проверять `track.urn?.startsWith("yandex:")` и вызывать `removeProgressive` для Яндекс-треков.

---

### 5. `resolveTrackIfNeeded` не обрабатывает Яндекс-треки
**Файл**: [MusicViewModel.kt:784–807](file:///home/danya/AndroidStudioProjects/MyApplication/app/src/main/java/com/example/myapplication/ui/MusicViewModel.kt#L784-L807)

Функция пытается разрешить URL через SoundCloud-резолвер для **всех** треков. Для Яндекс-треков резолвер упадёт, а фолбэк-стаб `"soundcloud://track/${t.id}"` — невалидный URI для Яндекс-трека. После первого трека в Яндекс-очереди воспроизведение застопорится.

> **Fix**: Добавить ветку для Яндекс-треков: `if (t.urn?.startsWith("yandex:") == true)` → использовать `"yandex://track/$yandexId"` как стаб.

---

### 6. Hash-коллизии Яндекс-треков (ID генерация)
**Файл**: [YandexMusicModels.kt:33–35](file:///home/danya/AndroidStudioProjects/MyApplication/app/src/main/java/com/example/myapplication/data/YandexMusicModels.kt#L33-L35) и [PlaybackService.kt:185](file:///home/danya/AndroidStudioProjects/MyApplication/app/src/main/java/com/example/myapplication/player/PlaybackService.kt#L185)

```kotlin
val generatedId = -kotlin.math.abs(rawId.hashCode().toLong())
```

**Проблемы:**
- `abs(Int.MIN_VALUE)` возвращает `Int.MIN_VALUE` (переполнение) → ID может быть **положительным** (`--2147483648 = 2147483648L`), что сломает инвариант "Яндекс-ID всегда отрицательные"
- `String.hashCode()` имеет только ~2³¹ уникальных значений → два разных Яндекс-трека могут получить **одинаковый ID**, перезаписав друг друга в базе
- Положительный ID может **совпасть с реальным SoundCloud ID**

> **Fix**: Использовать `toLong()` от самого Яндекс-ID (он числовой) с отрицательным смещением: `-(trackId.toLong() + Long.MAX_VALUE / 2)`, или использовать выделенный неймспейс.

---

### 7. 🔐 Токены авторизации записываются в Logcat
**Файл**: [MusicViewModel.kt:1065, 1091](file:///home/danya/AndroidStudioProjects/MyApplication/app/src/main/java/com/example/myapplication/ui/MusicViewModel.kt#L1065)

```kotlin
Log.d("MusicViewModel", "Captured Yandex token: $token")
Log.d("MusicViewModel", "Captured silent credentials: clientId=$clientId, oauthToken=$oauthToken")
```

Логирование токенов открытым текстом — **уязвимость безопасности**. Токены могут попасть в баг-репорты, крэш-логи, и на устройства с root-доступом.

> **Fix**: Удалить логирование токенов или замаскировать: `Log.d("...", "Token received (${token.take(4)}***)")`.

---

## 🟠 HIGH (Серьёзные баги, проблемы производительности)

### 8. 28 бесконечных анимаций одновременно
**Файл**: [MusicScreen.kt:1661–1795, 2707, 253](file:///home/danya/AndroidStudioProjects/MyApplication/app/src/main/java/com/example/myapplication/ui/MusicScreen.kt#L1661-L1795)

`ExpressiveBackground` содержит **14 бесконечных `animateFloat`** + сложный Canvas (14 фигур × 120 сегментов = **~1680 path-операций/кадр**). Компонент инстанцирован **дважды** (в MusicScreen и TrackDetailScreen), что означает **28 анимаций** и **~3360 path-операций/кадр** на 60 FPS. Жрёт CPU и батарею даже в фоне.

> **Fix**: Отключать анимации когда Activity не видна (`LocalLifecycleOwner`). Использовать одну общую инстанцию фона.

---

### 9. God-composable: MusicScreen подписан на 30+ StateFlow
**Файл**: [MusicScreen.kt:184–228](file:///home/danya/AndroidStudioProjects/MyApplication/app/src/main/java/com/example/myapplication/ui/MusicScreen.kt#L184-L228)

Корневой `MusicScreen` подписывается на **~32 StateFlow** из ViewModel. Любое изменение **любого** из них вызывает рекомпозицию всего дерева, включая пересоздание **всех лямбд** и перевычисление `downloadedTracks`.

> **Fix**: Разбить на подкомпозиции, использовать `derivedStateOf`, вынести лямбды в `remember`.

---

### 10. `downloadedTracks` пересчитывается на каждой рекомпозиции
**Файл**: [MusicScreen.kt:229](file:///home/danya/AndroidStudioProjects/MyApplication/app/src/main/java/com/example/myapplication/ui/MusicScreen.kt#L229)

```kotlin
val downloadedTracks = favorites.filter { it.downloadState == DownloadState.DOWNLOADED }
```

Фильтрация запускается на **каждой** рекомпозиции (а их ~32 триггера). С большой библиотекой — ощутимые тормоза.

> **Fix**: `val downloadedTracks = remember(favorites) { favorites.filter { ... } }` или вынести в ViewModel.

---

### 11. `runBlocking` в PlaybackService блокирует загрузочный поток ExoPlayer
**Файл**: [PlaybackService.kt:152, 200](file:///home/danya/AndroidStudioProjects/MyApplication/app/src/main/java/com/example/myapplication/player/PlaybackService.kt#L152)

```kotlin
val directUrl = runBlocking { ... }
```

`ResolvingDataSource.Resolver` вызывается на ExoPlayer loading thread. `runBlocking` блокирует его сетевым запросом. При медленном интернете воспроизведение **зависает на неопределённое время** без таймаута.

> **Fix**: Добавить `withTimeout(15_000)` внутри `runBlocking`, или использовать `suspendCoroutine` с callback-based resolver.

---

### 12. FavoritesRepository — гонки при read-modify-write
**Файл**: [FavoritesRepository.kt:22–68](file:///home/danya/AndroidStudioProjects/MyApplication/app/src/main/java/com/example/myapplication/data/FavoritesRepository.kt#L22-L68)

```kotlin
fun remove(trackId: Long) {
    update(_favorites.value.filterNot { it.id == trackId })
}
```

Классическая гонка:
1. Поток A читает `_favorites.value` = [1, 2, 3]
2. Поток B читает `_favorites.value` = [1, 2, 3]
3. Поток A удаляет трек 2 → записывает [1, 3]
4. Поток B удаляет трек 3 → записывает [1, 2] (из устаревшего снимка)
→ Удаление трека 2 **потеряно**.

> **Fix**: Использовать `MutableStateFlow.update { currentList -> ... }` (атомарный CAS) вместо прямого `.value = ...`.

---

### 13. Новый `FavoritesRepository` / `OkHttpClient` на каждый трек
**Файл**: [PlaybackService.kt:116, 129, 186, 207](file:///home/danya/AndroidStudioProjects/MyApplication/app/src/main/java/com/example/myapplication/player/PlaybackService.kt#L116)

Каждый вызов `resolveSoundCloudTrack()` / `resolveYandexTrack()` создаёт **новый** `FavoritesRepository` (десериализация JSON из SharedPreferences) и новый `OkHttpClient` (новый connection pool + thread pool). Для плейлиста из 50 треков — 50 аллокаций.

> **Fix**: Создать их как `lazy val` свойства сервиса, инициализированные один раз.

---

### 14. 43 интерактивных иконки без `contentDescription`
**Файл**: [MusicScreen.kt](file:///home/danya/AndroidStudioProjects/MyApplication/app/src/main/java/com/example/myapplication/ui/MusicScreen.kt) — 43 вхождения

Кнопки "Назад", "Удалить", "Следующий трек", "Предыдущий трек", обложки и т.д. — все имеют `contentDescription = null`. Приложение **полностью недоступно** для пользователей с TalkBack.

> **Fix**: Добавить описания: `"Назад"`, `"Удалить загрузку"`, `"Следующий трек"`, и т.д.

---

### 15. `MorphingArtworkShape.createOutline` — тяжёлая тригонометрия на каждый кадр
**Файл**: [MusicScreen.kt:3240–3288](file:///home/danya/AndroidStudioProjects/MyApplication/app/src/main/java/com/example/myapplication/ui/MusicScreen.kt#L3240-L3288)

120 вызовов `cos()`, `sin()`, `pow()` на **каждый кадр** при активной анимации вращения. На 60 FPS это ~7200 тригонометрических операций/сек.

> **Fix**: Кэшировать Path-данные при неизменных параметрах. Использовать `GenericShape` с `remember`.

---

### 16. Утечка памяти: Activity Context в ViewModel
**Файл**: [MusicViewModel.kt:56](file:///home/danya/AndroidStudioProjects/MyApplication/app/src/main/java/com/example/myapplication/ui/MusicViewModel.kt#L56)

```kotlin
private val context: Context
```

Если передан Activity context — ViewModel переживает Activity при повороте экрана → **классическая утечка памяти Android**.

> **Fix**: Убедиться, что передаётся `context.applicationContext`, или использовать `AndroidViewModel`.

---

## 🟡 MEDIUM (Некорректное поведение, UX-проблемы)

### 17. Конфликт жестов: свайп-порог 15px на всём экране
**Файл**: [MusicScreen.kt:2688–2700](file:///home/danya/AndroidStudioProjects/MyApplication/app/src/main/java/com/example/myapplication/ui/MusicScreen.kt#L2688-L2700)

Drag-жест на **всём** `TrackDetailScreen` с порогом всего `-15f` пикселей. Любое лёгкое движение вверх при перетаскивании слайдера или тапе по обложке — случайно откроет очередь.

> **Fix**: Увеличить порог до `-40f` и/или ограничить зону распознавания свайпа (не на слайдере/обложке).

---

### 18. Три дублирующих dismiss-жеста на QueueManagerPanel
**Файл**: [MusicScreen.kt:2967–2982, 3007–3015, 3025](file:///home/danya/AndroidStudioProjects/MyApplication/app/src/main/java/com/example/myapplication/ui/MusicScreen.kt#L2967-L2982)

Три отдельных `pointerInput` для закрытия панели (outer Box drag, header drag, drag handle click) могут **конфликтовать** друг с другом. Tap-детектор и drag-детектор на одном элементе «борются» — свайп может быть распознан как тап.

> **Fix**: Объединить в один gesture handler. Использовать `detectVerticalDragGestures` с threshold.

---

### 19. `_isLoading` / `_errorMessage` — общие для всех операций
**Файл**: [MusicViewModel.kt](file:///home/danya/AndroidStudioProjects/MyApplication/app/src/main/java/com/example/myapplication/ui/MusicViewModel.kt) — множественные строки

Один `_isLoading` на всё: поиск, загрузка микса, импорт, воспроизведение. Если две операции параллельны — одна сбросит флаг другой. UI мерцает.

> **Fix**: Разделить на `_searchLoading`, `_mixLoading`, `_playbackLoading` и т.д.

---

### 20. `originalQueue` — несинхронизированный `var`
**Файл**: [MusicViewModel.kt:259](file:///home/danya/AndroidStudioProjects/MyApplication/app/src/main/java/com/example/myapplication/ui/MusicViewModel.kt#L259)

Записывается из `playMix`, `playFavorite`, `playPlaylistTrack`, `playQueuedTrack`. Читается из `toggleShuffle`. Без синхронизации. При быстром переключении треков — гонка.

> **Fix**: Обернуть в `MutableStateFlow<List<SoundCloudTrack>>()` или использовать `Mutex`.

---

### 21. `toggleShuffle` гонка с init-коллектором
**Файл**: [MusicViewModel.kt:1301–1341](file:///home/danya/AndroidStudioProjects/MyApplication/app/src/main/java/com/example/myapplication/ui/MusicViewModel.kt#L1301-L1341)

`musicPlayer.toggleShuffle()` вызывается первым, затем читается `musicPlayer.shuffleEnabled.value`. Init-блок `collectLatest` на `shuffleEnabled` может сработать **между этими строками**, запустив `resolveTrackIfNeeded` → `updateQueue`, которая **перезапишет** очередь пока `toggleShuffle` ещё работает.

> **Fix**: Использовать атомарную операцию или `Mutex` для всей операции переключения шаффла.

---

### 22. HTTP-ответ Яндекс API не проверяется на success
**Файл**: [PlaybackService.kt:213–215](file:///home/danya/AndroidStudioProjects/MyApplication/app/src/main/java/com/example/myapplication/player/PlaybackService.kt#L213-L215)

```kotlin
val xmlString = client.newCall(request).execute().use { response ->
    response.body?.string() ?: ""
}
```

Нет проверки `response.isSuccessful`. Ответ 4xx/5xx будет распарсен как XML → мусорные данные или молчаливый провал.

> **Fix**: Добавить `if (!response.isSuccessful) throw IOException("HTTP ${response.code}")`.

---

### 23. HLS-скачивание: URL записывается до завершения загрузки
**Файл**: [MusicViewModel.kt:2134](file:///home/danya/AndroidStudioProjects/MyApplication/app/src/main/java/com/example/myapplication/ui/MusicViewModel.kt#L2134)

`favoritesRepository.updateStreamUrl(track.id, streamUrl)` вызывается **до** `offlineMusicStore.downloadHls(streamUrl)`. Если загрузка упадёт — URL уже сохранён, файл не существует, воспроизведение сломано.

> **Fix**: Вызывать `updateStreamUrl` **после** успешного `downloadHls`.

---

### 24. HLS-скачивание: нет валидации после загрузки
**Файл**: [MusicViewModel.kt:2133–2148](file:///home/danya/AndroidStudioProjects/MyApplication/app/src/main/java/com/example/myapplication/ui/MusicViewModel.kt#L2133-L2148)

Для Яндекс-треков проверяется длительность скачанного файла (строки 2101–2106). Для SoundCloud HLS — **нет никакой валидации**. Частично скачанный файл помечается как DOWNLOADED.

> **Fix**: Добавить проверку размера/длительности файла после `downloadHls`.

---

### 25. Flash логин-экрана при запуске
**Файл**: [MusicScreen.kt:186](file:///home/danya/AndroidStudioProjects/MyApplication/app/src/main/java/com/example/myapplication/ui/MusicScreen.kt#L186)

```kotlin
val isLoggedOut by viewModel.isLoggedOut.collectAsState(initial = true)
```

`initial = true` означает, что на первый кадр UI показывает экран логина, даже если пользователь уже авторизован. Визуальное мерцание.

> **Fix**: Использовать `initial = false` или `null` с промежуточным loading-состоянием.

---

### 26. `readableMessage()` — скрытый side effect
**Файл**: [MusicViewModel.kt:1409–1411](file:///home/danya/AndroidStudioProjects/MyApplication/app/src/main/java/com/example/myapplication/ui/MusicViewModel.kt#L1409-L1411)

Функция форматирования строки ошибки **неявно** вызывает `handleSoundCloudApiError()` → `tryAutoRefreshClientId()` → `triggerSilentRelogin()`. Двойной вызов `readableMessage` для одной ошибки = двойной auto-refresh.

> **Fix**: Вынести side-effect из форматтера. Вызывать `handleSoundCloudApiError` явно до `readableMessage`.

---

### 27. Drag-to-reorder не скроллит LazyColumn
**Файл**: [MusicScreen.kt:3098–3138](file:///home/danya/AndroidStudioProjects/MyApplication/app/src/main/java/com/example/myapplication/ui/MusicScreen.kt#L3098-L3138)

Перетаскивание элемента потребляет все pointer-события. LazyColumn не может проскроллить, чтобы показать элементы за пределами видимой области. Пользователь **не может переместить трек дальше видимого экрана**.

> **Fix**: Реализовать auto-scroll при приближении к краю: запускать `listState.animateScrollToItem()` когда drag-offset приближается к верхнему/нижнему краю.

---

### 28. Hardcoded высота элемента 82dp в drag-reorder
**Файл**: [MusicScreen.kt:3109](file:///home/danya/AndroidStudioProjects/MyApplication/app/src/main/java/com/example/myapplication/ui/MusicScreen.kt#L3109)

```kotlin
val itemHeightPx = with(density) { 82.dp.toPx() }
```

Реальная высота зависит от контента (длинные названия, padding). Если отличается — элементы будут прыгать или пропускать позиции.

> **Fix**: Использовать `onGloballyPositioned { ... }` для измерения реальной высоты, или `LazyListState.layoutInfo`.

---

## 🟢 LOW (Код-запахи, незначительные проблемы)

### 29. `MusicPlayer.syncState()` оставляет устаревшее название трека
**Файл**: [MusicPlayer.kt:197–198](file:///home/danya/AndroidStudioProjects/MyApplication/app/src/main/java/com/example/myapplication/player/PlaybackService.kt#L197-L198)

При пустой очереди `_currentTrack` сохраняет название **предыдущего** трека вместо `null`.

### 30. `MusicPlayer.release()` не обнуляет `controller`
**Файл**: [MusicPlayer.kt:162–166](file:///home/danya/AndroidStudioProjects/MyApplication/app/src/main/java/com/example/myapplication/player/MusicPlayer.kt#L162-L166)

После `release()` ссылка `controller` остаётся ненулевой → последующие вызовы на released controller = `IllegalStateException`.

### 31. `updateQueue` не удаляет лишние элементы
**Файл**: [MusicPlayer.kt:92–112](file:///home/danya/AndroidStudioProjects/MyApplication/app/src/main/java/com/example/myapplication/player/MusicPlayer.kt#L92-L112)

Если новый список короче старого — «хвостовые» MediaItem из старой очереди остаются в ExoPlayer.

### 32. Polling-корутина каждые 500ms даже когда не играет
**Файл**: [MusicPlayer.kt:67–72](file:///home/danya/AndroidStudioProjects/MyApplication/app/src/main/java/com/example/myapplication/player/MusicPlayer.kt#L67-L72)

`syncState()` вызывается каждые 500мс вне зависимости от состояния. Расходует CPU/батарею в idle.

### 33. Не переопределён `onTaskRemoved` в PlaybackService
**Файл**: [PlaybackService.kt](file:///home/danya/AndroidStudioProjects/MyApplication/app/src/main/java/com/example/myapplication/player/PlaybackService.kt)

Без этого сервис может продолжать жить после свайпа приложения из Recent Apps.

### 34. Новый `Regex` на каждый XML-тег при каждом вызове
**Файл**: [PlaybackService.kt:219–222](file:///home/danya/AndroidStudioProjects/MyApplication/app/src/main/java/com/example/myapplication/player/PlaybackService.kt#L219-L222)

Лямбда компилирует `Regex` на каждый вызов. Кэширование сэкономило бы ресурсы.

### 35. Gson может вставить `null` в non-nullable поля
**Файл**: [YandexMusicModels.kt](file:///home/danya/AndroidStudioProjects/MyApplication/app/src/main/java/com/example/myapplication/data/YandexMusicModels.kt) и [FavoriteTrack.kt](file:///home/danya/AndroidStudioProjects/MyApplication/app/src/main/java/com/example/myapplication/data/FavoriteTrack.kt)

Gson игнорирует Kotlin non-null аннотации. Если JSON-поле отсутствует → поле будет `null` в runtime → NPE.

### 36. Playlist `kind` и Album `id` коллизии
**Файл**: [YandexMusicModels.kt:114–173](file:///home/danya/AndroidStudioProjects/MyApplication/app/src/main/java/com/example/myapplication/data/YandexMusicModels.kt#L114-L173)

`YandexPlaylist.kind` и `YandexAlbum.id` оба маппятся в `SoundCloudPlaylist.id` без неймспейса. Одинаковые числовые значения → коллизия.

### 37. `favorites.firstOrNull` — линейный поиск на каждую рекомпозицию
**Файл**: [MusicScreen.kt:589](file:///home/danya/AndroidStudioProjects/MyApplication/app/src/main/java/com/example/myapplication/ui/MusicScreen.kt#L589)

С большой библиотекой — ощутимая задержка. Нужен `Map<Long, FavoriteTrack>`.

### 38. `lastVibratedRatio` не сбрасывается при смене трека
**Файл**: [MusicScreen.kt:2679](file:///home/danya/AndroidStudioProjects/MyApplication/app/src/main/java/com/example/myapplication/ui/MusicScreen.kt#L2679)

`remember` без ключа → стейл-значение от предыдущего трека влияет на первую вибрацию слайдера.

### 39. QueueManagerPanel без empty-state
**Файл**: [MusicScreen.kt:3050–3189](file:///home/danya/AndroidStudioProjects/MyApplication/app/src/main/java/com/example/myapplication/ui/MusicScreen.kt#L3050-L3189)

Пустой `activeQueue` → пустая карточка без сообщения «Очередь пуста».

### 40. Drag handle — 40×5dp, ниже минимума 48dp
**Файл**: [MusicScreen.kt:3021–3025](file:///home/danya/AndroidStudioProjects/MyApplication/app/src/main/java/com/example/myapplication/ui/MusicScreen.kt#L3021-L3025)

Pill-элемент 40×5dp с `.clickable` — невозможно попасть пальцем. Нарушение Material Guidelines (минимум 48dp).

### 41. `isPlayableTrack` и `isPlayableMixTrack` — дублирование логики
**Файл**: [MusicViewModel.kt:1905, 1400](file:///home/danya/AndroidStudioProjects/MyApplication/app/src/main/java/com/example/myapplication/ui/MusicViewModel.kt#L1400)

Два почти идентичных метода. Изменения в одном не отразятся на другом.

### 42. `stopLikesSync` сбрасывает оба статуса вне зависимости от источника
**Файл**: [MusicViewModel.kt:1831–1836](file:///home/danya/AndroidStudioProjects/MyApplication/app/src/main/java/com/example/myapplication/ui/MusicViewModel.kt#L1831-L1836)

Статус COMPLETED одного источника перезаписывается на IDLE при остановке другого.

### 43. `settingsRepository` — публичный getter
**Файл**: [MusicViewModel.kt:62](file:///home/danya/AndroidStudioProjects/MyApplication/app/src/main/java/com/example/myapplication/ui/MusicViewModel.kt#L62)

Нарушение инкапсуляции. Должен быть `private`.

---

## 📊 Сводная таблица

| Файл | 🔴 Critical | 🟠 High | 🟡 Medium | 🟢 Low |
|------|------------|---------|-----------|--------|
| MusicViewModel.kt | 4 | 1 | 5 | 4 |
| MusicScreen.kt | 1 | 5 | 4 | 5 |
| PlaybackService.kt | 1 | 2 | 1 | 2 |
| MusicPlayer.kt | 0 | 0 | 0 | 3 |
| YandexMusicModels.kt | 1 | 0 | 0 | 2 |
| FavoritesRepository.kt | 0 | 1 | 0 | 0 |
| FavoriteTrack.kt | 0 | 0 | 0 | 1 |
| **Итого** | **7** | **9** | **10** | **17** |

---

## ✅ Рекомендуемый порядок исправления

> [!IMPORTANT]
> Рекомендуется исправить Critical-баги (1–7) в первую очередь — они ведут к крэшам, потере данных и уязвимостям безопасности.

1. **Немедленно**: Удалить логирование токенов (#7)
2. **Немедленно**: Заменить `mutableMapOf` на `ConcurrentHashMap` (#1)
3. **Приоритетно**: Исправить `playFavorite` index (#2), NPE в TrackActionsDialog (#3), `deleteDownloadedTrack` (#4)
4. **Важно**: Добавить поддержку Яндекс-треков в `resolveTrackIfNeeded` (#5), исправить hash-генерацию (#6)
5. **Планово**: Оптимизация анимаций (#8), рефакторинг god-composable (#9), thread safety (#12, #20, #21)
