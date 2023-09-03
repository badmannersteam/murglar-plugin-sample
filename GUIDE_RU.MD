# Система плагинов, описание и примеры.

В данном гайде подробно описан весь процесс создания плагина.

Не забывайте смотреть в код проекта данного примера, он хоть и написан для несуществующего сервиса и только ради
примера, однако представляет собой полноценный плагин с точки зрения кода.
Также не забывайте про документацию в коде `core` модуля.

## Общий процесс создания плагина

1. Создание проекта и подключение зависимостей
2. Создание моделей объектов (треков/альбомов/артистов/плейлистов/etc)
3. Создание классов локализации
4. Реализация `Murglar` - основной интерфейс плагина, точка входа
5. Реализация `LoginResolver` - если сервис поддерживает авторизацию
6. Реализация `NodeResolver` - навигация по сервису, поиск, обработка ссылок/лайков/событий
7. Реализация `Decryptor` - если сервис предоставляет зашифрованный контент
8. Сборка

## Создание проекта, подключение Gradle плагинов и зависимостей

Описано в [README основного проекта](https://github.com/badmannersteam/murglar-plugins) - разделы Installation и Usage.

Имейте в виду, что хост приложение (которое выполняет плагины) имеет в classpath core модуль c его зависимостями
(`kotlin-stdlib`, `kotlinx-serialization-json`, `threetenbp`, `apache commons-text`) и реализации
`NetworkMiddleware`, `PreferenceMiddleware`, `NotificationMiddleware` и `LoggerMiddleware`.

Все остальные зависимости
(например protobuf/ftp-клиент/etc), если они нужны вашему плагину, должны быть подключены явно в блоке
`dependencies {...}` в `<plugin_name>-core/build.gradle.kts`.

## Модели объектов

Все объекты (треки/альбомы/артисты/плейлисты/радио/промежуточные папки/etc), которые будут доступны снаружи плагина
(т.е. являться аргументами/возвращаемыми значениями его публичных методов), должны быть представлены в виде классов,
наследующихся от `com.badmanners.murglar.lib.core.model.node.Node`,
и иметь аннотацию `@com.badmanners.murglar.lib.core.utils.contract.Model`, создающую конструктор без параметров
(это требуется для сериализации этих объектов в хост-приложениях).

Подробнее о `Node` смотрите в разделе
[Навигация по сервису](#noderesolver---навигация-по-сервису-поиск-обработка-ссылоклайковсобытий).

### Базовые классы

Используйте `BaseXxx` классы (которые являются наследниками `Node` и `MutableNode`) в качестве предков:

- для треков/эпизодов подкастов/глав аудиокниг/прочих аудио - `com.badmanners.murglar.lib.core.model.track.BaseTrack`
- для альбомов - `com.badmanners.murglar.lib.core.model.album.BaseAlbum`
- для артистов - `com.badmanners.murglar.lib.core.model.artist.BaseArtist`
- для плейлистов - `com.badmanners.murglar.lib.core.model.playlist.BasePlaylist`
- для радио - `com.badmanners.murglar.lib.core.model.radio.BaseRadio`
- для промежуточных папок - `com.badmanners.murglar.lib.core.model.node.BaseNode`

### Тип модели (`NodeType`)

Не забывайте указывать правильный тип объекта, если он не совпадает с типом в классе-предке:

```kotlin
// например передавая его в конструкторе
@Model
class SampleTrack(
    //...
    override val nodeType: String
) : BaseTrack(/*...*/)

// или делая зависимым от других полей
@Model
class SamplePlaylist(
    //...
    val isAlbum: Boolean
) : BasePlaylist(/*...*/) {
    override val nodeType = if (isAlbum) NodeType.ALBUM else NodeType.PLAYLIST
}
```

### Поля объектов и сериализация

При проектировании набора полей в объектах старайтесь соблюдать баланс:

- не создавайте поля под каждый атрибут из API сервиса
- постарайтесь предусмотреть все поля, необходимые для реализации всей желаемой функциональности,
  чтобы лишний раз не провоцировать пересоздание кэша в хост-приложении из-за изменившегося набора полей.

**НЕ** объявляйте `serialVersionUid` в объектах - в случае ошибок десериализации хост-приложение перезапросит
необходимую информацию.

## Сообщения и локализация

Для локализации используется подход, при котором создается интерфейс со строками-ключами, а реализации этого
интерфейса предоставляют реальное значение в нужной локализации. Смена локализаций прозрачно осуществляется при помощи
`com.badmanners.murglar.lib.core.localization.DelegatingMessages`
(см. `com.badmanners.murglar.lib.core.service.BaseMurglar#messages`).

Итого для локализации требуется:

1. Создать свой интерфейс, наследующийся от `com.badmanners.murglar.lib.core.localization.Messages`
   (в нем содержатся базовые ключи, которые тоже можно использовать), и объявить в нем необходимые ключи:
   ```kotlin
   interface SampleMessages : Messages {
       val loginHelpText: String
       val twoFAText: String
   } 
   ```
2. Создать реализации для каждой локализации (как минимум английской (`DefaultXxx`) и русской (`RuXxx`)),
   наследующиеся от интерфейса, созданного в предыдущем пункте, и реализации `Messages` на соответствующем языке:
   ```kotlin
   object SampleDefaultMessages : DefaultMessages(), SampleMessages {
       override val serviceName = "Sample"
       override val loginHelpText = "Login help text here"
       override val twoFAText = "2FA code from SMS"
   }
   ```
   ```kotlin
   object SampleRuMessages : RuMessages(), SampleMessages {
       override val serviceName = "Пример"
       override val loginHelpText = "Текст помощи со входом"
       override val twoFAText = "Код подтверждения из SMS"
   }
   ```
3. Созданные реализации будут использованы далее в конструкторе
   `com.badmanners.murglar.lib.core.service.BaseMurglar#localeToMessages` и могут быть использованы через поле
   `com.badmanners.murglar.lib.core.service.BaseMurglar#messages`.

## Murglar - интерфейс плагина

Интерфейс `com.badmanners.murglar.lib.core.service.Murglar` является точкой входа, через которую хост-приложение
взаимодействует с плагином.

Как основу для реализации стоит использовать класс `com.badmanners.murglar.lib.core.service.BaseMurglar`, который
управляет локализацией и предоставляет `Messages` и основные `Middleware`.

`Middleware` - абстракции, которые позволяют плагину:

- `com.badmanners.murglar.lib.core.network.NetworkMiddleware` - выполнять HTTP запросы
    - `com.badmanners.murglar.lib.core.network.NetworkRequest` - абстракция запроса
    - `com.badmanners.murglar.lib.core.network.NetworkResponse` - абстракция ответа
    - `com.badmanners.murglar.lib.core.network.ResponseConverters` - стандартные конвертеры ответов в строки/байты/JSON
- `com.badmanners.murglar.lib.core.preference.PreferenceMiddleware` - сохранять настройки/данные между перезапусками
- `com.badmanners.murglar.lib.core.notification.NotificationMiddleware` -
  выводить уведомления пользователю (тосты/снекбары)
- `com.badmanners.murglar.lib.core.log.LoggerMiddleware` - выполнять логгирование

Реализации `Middleware` плагину предоставит в рантайме хост-приложение.

### Реализация `Murglar`

Создайте класс, наследующийся от `BaseMurglar`, используя для типизации созданные ранее класс трека и интерфейс
сообщений, а также объявите и передайте в конструктор `BaseMurglar`:

- идентификатор плагина
- ссылку на иконку (цветная, 512px PNG)
- маппинг `Locale`->`Messages`
- все `Middleware`

Этот конструктор будет вызван хост-приложением и для `Middleware` будут переданы их реализации,
а для `id: String` будет передано значение `murglarPlugin.id`/`murglarAndroidPlugin.id` из
`build.gradle.kts` в зависимости от платформы.

```kotlin
class SampleMurglar(
    id: String,
    preferences: PreferenceMiddleware,
    network: NetworkMiddleware,
    notifications: NotificationMiddleware,
    logger: LoggerMiddleware
) : BaseMurglar<SampleTrack, SampleMessages>(id, ICON_URL, MESSAGES, preferences, network, notifications, logger) {

    companion object {
        private const val ICON_URL =
            "https://play-lh.googleusercontent.com/aFWiT2lTa9CYBpyPjfgfNHd0r5puwKRGj2rHpdPTNrz2N9LXgN_MbLjePd1OTc0E8Rl1"

        private val MESSAGES = mapOf(
            ENGLISH to SampleDefaultMessages,
            RUSSIAN to SampleRuMessages
        )
    }
}
```

### Настройки плагина

Плагин может предоставить пользователю следующие типы настроек (см. `com.badmanners.murglar.lib.core.preference`):

- `ActionPreference` - вызов действия (напр. принудительное обновление сессии/токена)
- `CopyPreference` - копирование текста (напр. копирование токена/cookie/никнейма)
- `EditPreference` - редактирование значения (напр. изменение домена/ключа API)
- `ListPreference` - выбор из списка
- `SwitchPreference` - переключатель

Объявление настроек:

```kotlin
override val murglarPreferences: List<Preference>
    get() = mutableListOf<Preference>().apply {

        if (loginResolver.isLogged)
            this += CopyPreference(
                id = "token",
                title = messages.copyToken,
                getter = loginResolver::oauthToken,
                displayGetter = { loginResolver.oauthToken.mask(5, 5) }
            )

        this += ActionPreference(
            id = "action",
            title = "Hello world!",
            summary = "Shows hello world",
            action = { notifications.longNotify("Hello world!") },
            needConfirmation = true,
            confirmationText = "You will see a message!"
        )

        //more preferences
    }
```

### Список доступных форматов

Этот список позволяет пользователю выбрать формат по умолчанию для плеера/загрузчика/etc.

Большинство музыкальных сервисов предоставляют фиксированный набор доступных форматов контента. В случаях,
когда расширение или битрейт неизвестны заранее, можно воспользоваться значениями `Extension.UNKNOWN` и
`Bitrate.B_UNKNOWN`.

Список должен быть объявлен в порядке ухудшения качества контента.
Например, для сервиса, который предоставляет возможность скачивать исходник трека (в неизвестном заранее формате
и битрейте), а также 320 MP3 и 192 MP3, список будет выглядеть так:

```kotlin
override val possibleFormats = listOf(
    Extension.UNKNOWN to Bitrate.B_UNKNOWN,
    Extension.MP3 to Bitrate.B_320,
    Extension.MP3 to Bitrate.B_192
)
```

### Жизненный цикл

У плагина есть только один метод жизненного цикла - `onCreate()`, вызывающийся после запуска хост-приложения и создания
экземпляра `Murglar`.
В нем можно при необходимости выполнить различные действия - например, обновить сессию/токены/ключи.

```kotlin
@WorkerThread
override fun onCreate() {
    if (!loginResolver.isLogged)
        return

    try {
        loginResolver.updateUser()
    } catch (e: MessageException) {
        notifications.longNotify(e.message!!)
    }
}
```

### Резолвинг URL контента

В большинстве сервисов URL контента не доступен сразу в момент конвертации ответа от API в модель, либо быстро
устаревает. В таких случаях URL должен резолвиться позже по необходимости при помощи дополнительного запроса/запросов.
Метод `resolveSourceForUrl` позволяет выполнить это, получая в аргументах объект трека и `Source`,
который нужно вернуть с уже готовым URL контента:

```kotlin
@WorkerThread
override fun resolveSourceForUrl(track: SampleTrack, source: Source): Source {
    val request = getRequest("${API_V2_DOMAIN}/track/${track.id}/download").build()
    val result = network.execute(request, ResponseConverters.asJsonObject()).result

    val actualCodec = result.getString("codec")
    val requiredCodec = source.extension.value
    check(actualCodec == requiredCodec) {
        "${messages.sourceUrlUnavailable} Wrong codec - required '$requiredCodec', actual '$actualCodec'!"
    }

    val actualBitrate = result.getInt("bitrate")
    val requiredBitrate = source.bitrate.value
    check(actualBitrate == requiredBitrate) {
        "${messages.sourceUrlUnavailable} Wrong bitrate - required '$requiredBitrate', actual '$actualBitrate'!"
    }
    check(!result.getBoolean("preview")) {
        "${messages.sourceUrlUnavailable} Got preview version of track!"
    }

    val url = result.getStringOpt("url") ?: error(messages.sourceUrlUnavailable)

    return source.copyWithNewUrl(url)
}
```

В случаях, когда сервер по какой-то причине вернул URL не для того `Source`, для которого он запрашивался, требуется
выбросить исключение, иначе возможно некорректное поведение хост-приложения.

Если URL контента доступен сразу в момент конвертации ответа от API в модель и он не устаревает, то URL можно сразу
сохранить в `Source.url` в момент создания, а в методе `resolveSourceForUrl` вернуть `Source` без модификаций.

### Тексты песен

Тексты песен могут быть как простыми, так и синхронизированными -
см. `com.badmanners.murglar.lib.core.model.tag.Lyrics`.

Если сервис поддерживает получение текста песен, следует переопределить метод `hasLyrics`:

```kotlin
override fun hasLyrics(track: SampleTrack) = track.hasLyrics 
```

Метод `hasLyrics` обязательно должен быть неблокирующим и выполняться быстро.
Если достоверно неизвестно, есть ли текст, но он может быть - лучше вернуть `true`.

Для получения самого текста будет вызван метод `getLyrics`, в котором нужно запросить из сервиса текст:

```kotlin
@WorkerThread
override fun getLyrics(track: SampleTrack): Lyrics {
    val request = trackRequest(track.id, track.albumId)
    val response = network.execute(request, ResponseConverters.asJsonObject())

    val result = response.result.getJsonObject("result")

    return when (result.getStringOpt("type")) {
        "plain" -> Lyrics(result.getString("lyrics"))

        "synced" -> {
            val lyrics = result.getString("lyrics")

            // [00:18.52] Line 1\n
            // [00:22.82] Line 2\n
            val plain = lyrics.replace("\\[.+?] ".toRegex(), "")

            val lines = lyrics.split('\n').map {
                val startMillis = it.substring(1, 3).toLong() * 60000 +
                        it.substring(4, 6).toLong() * 1000 +
                        it.substring(7, 9).toLong() * 10
                val line = it.substring(11)

                SyncedLyrics.Line(startMillis, null, line)
            }
            val synced = SyncedLyrics(lines, track.artistNames[0], track.title, track.albumName)

            Lyrics(plain, synced)
        }

        else -> error(messages.trackHasNoLyrics)
    }
}
```

Не храните текст в объекте трека - это значительно увеличивает размер объект и отрицательно влияет на производительность
хост-приложения из-за постоянно выполняющейся сериализации/десериализации.

### Теги

Теги в плагине представлены объектом `com.badmanners.murglar.lib.core.model.tag.Tags`, ответственность за реализацию
самого теггинга и поддержка разных форматов и контейнеров лежит на хост-приложении.
От плагина требуется только заполнить этот объект данными в методе `getTags`:

```kotlin
@WorkerThread
override fun getTags(track: SampleTrack): Tags {
    val tags = Tags.Builder()
        .title(track.title)
        .subtitle(track.subtitle)
        .artists(track.artistNames)
        .genre(track.genre)
        .explicit(track.explicit)
        .gain(track.gain)
        .peak(track.peak)
        .url(track.serviceUrl)
        .mediaId(track.mediaId)

    if (!track.hasAlbum)
        return tags.createTags()

    tags.album(track.albumName)
        .albumArtist(track.albumArtist)
        .trackNumber(track.indexInAlbum)
        .diskNumber(track.volumeNumber)
        .releaseDate(track.albumReleaseDate)

    val volumesAndTracks = mutableMapOf<Int, Int>()
    getAlbumTracks(track.albumId!!).forEach {
        val tracks = volumesAndTracks[it.volumeNumber] ?: 0
        volumesAndTracks[it.volumeNumber!!] = tracks + 1
    }
    tags.totalDisks(volumesAndTracks.keys.size)
        .totalTracks(volumesAndTracks[track.volumeNumber])

    return tags.createTags()
}
```

В `BaseMurglar` есть базовая реализация этого метода, для кастомизации следует переопределить его.
Заполнять объект тегов можно как данными из модели трека, так и выполняя дополнительные запросы к API сервиса.

### Перезагрузка треков из сервиса для восстановления кэша

В хост-приложении могут возникать моменты, когда в кэше есть контент треков, но нет метаданных (моделей) для них,
из-за чего приложение не может отобразить их.
Для быстрого восстановления метаданных существует метод `getTracksByMediaIds`, принимающий на вход список MediaIds
(см. `com.badmanners.murglar.lib.core.model.track.BaseTrack#mediaId` и `com.badmanners.murglar.lib.core.utils.MediaId`):

```kotlin
@WorkerThread
override fun getTracksByMediaIds(mediaIds: List<String>): List<SampleTrack> = mediaIds
    .map { MediaId.getIds(it).first() }
    .loadPaged(1000) {
        val request = postRequest("$API_DOMAIN/track-entries")
            .addParameter("entries", it.prepareEntriesString())
            .build()
        val response = network.execute(request, ResponseConverters.asJsonArray())
        response.result.toTracks()
    }
```

Метод должен вернуть из сервиса как можно больше доступных объектов треков.
Если метод невозможно реализовать из-за отсутствия нужного API в сервисе, то он должен вернуть пустой список.

### Реализация основных запросов

На этом этапе остается реализовать все методы/запросы, которые будут выполнять основную функциональность плагина:

- Получение библиотеки пользователя
- Получение объектов и их содержимого (треков, альбомов, плейлистов, артистов, etc)
- Запросы поиска
- Методы выполнения лайков/дизлайков
- Уведомления сервиса о событиях начала/конца трека
- ...

Смотрите `com.badmanners.murglar.lib.sample.SampleMurglar` для примера реализации.

## `LoginResolver` - логин в сервисе

Для управления логином в сервисе есть интерфейс `com.badmanners.murglar.lib.core.login.LoginResolver`
(см. документацию), который позволяет хост-приложению получать информацию о текущем статусе логина и
доступных способах логина, а также выполнять логин/логаут.

Если сервис не поддерживает логин - используйте `com.badmanners.murglar.lib.core.login.NoLoginResolver`.

`LoginResolver` поддерживает 2 типа логина, для каждого из которых можно объявить несколько вариантов:

1. через WebView (метод `webLogin`) - пользователю открывается официальная страница входа в сервис, пользователь
   логинится, по URL/запросам определяется что пользователь совершил вход, cookie WebView используются для получения
   токена/cookie/сессии/etc.
   ```kotlin
   override val webLoginVariants = listOf(
       WebLoginVariant(
           id = WEB_LOGIN_VARIANT_1,
           label = { "${messages.loginWith(web = true)} v1" }
       ),
       WebLoginVariant(
           id = WEB_LOGIN_VARIANT_2,
           label = { "${messages.loginWith(web = true)} v2" }
       )
   )
   ```
2. по учетным данным (метод `credentialsLogin`) - пользователь вводит почту/логин/пароль/токен/значение cookie/что
   угодно, что позволяет в дальнейшем выполнять запросы, используя эти данные для авторизации
   ```kotlin
   override val credentialsLoginVariants = listOf(
       CredentialsLoginVariant(
           id = TOKEN_LOGIN_VARIANT,
           label = { messages.loginWith(tokens = true) },
           credentials = listOf(
               Credential(OAUTH_TOKEN_CREDENTIAL, messages::oauthToken)
           )
       ),
       CredentialsLoginVariant(
           id = EMAIL_LOGIN_VARIANT,
           label = { messages.loginWith(email = true) },
           credentials = listOf(
               Credential(EMAIL_CREDENTIAL, { "Email" }),
               Credential(PASSWORD_CREDENTIAL, messages::password)
           )
       ),
       CredentialsLoginVariant(
           id = COOKIE_LOGIN_VARIANT,
           label = { messages.loginWith(cookies = true) },
           credentials = listOf(
               Credential(COOKIE_CREDENTIAL, { "Cookie" })
           )
       )
   )
   ```

Все варианты будут отображены пользователю хост-приложением.
Плагину остается лишь обработать их в соответствующих методах, когда пользователь выберет один из них.
Для примера обработки смотрите `com.badmanners.murglar.lib.sample.login.SampleLoginResolver`, а также документацию
`com.badmanners.murglar.lib.core.login.LoginResolver` и `com.badmanners.murglar.lib.core.login.CredentialLoginStep`.

Имейте в виду, что для реализации логина можно использовать:

- `NetworkMiddleware`, в котором после осуществления пользователем входа через webview окажутся все cookies из webview,
  что позволяет:
    - выполнять запросы из контекста залогиненого пользователя (напр. для получения постоянного токена)
    - извлекать из cookies токены (если они там есть)
- `PreferenceMiddleware`, в который можно сохранять токены/логины/флаг факта логина по cookies/etc

Также можно действовать в обратном направлении - если для выполнения запроса сервис требует набор cookies,
то в случае web логина достаточно сохранить в `PreferenceMiddleware` флаг факта логина (cookies после логина будут
добавлены из webview в `NetworkMiddleware`), а при credentials логине запросить у пользователя значение cookies,
создать cookies, добавить ее в `NetworkMiddleware`, проверить актуальность каким-нибудь запросом, требующим авторизацию,
и сохранить в `PreferenceMiddleware` флаг факта логина.

## `NodeResolver` - навигация по сервису, поиск, обработка ссылок/лайков/событий

### `Node` - абстракция для моделей объектов

Все модели объектов являются наследниками интерфейса `com.badmanners.murglar.lib.core.model.node.Node`,
который является абстракцией, позволяющей унифицированно представить контент плагина в виде иерархии папок
(альбомы/артисты/плейлисты/папки/etc) и треков.

Каждая `Node` содержит:

- `nodeId` - идентификатор элемента
- `nodeName` - имя (напр. название трека/альбома/плейлиста/папки, имя артиста и т.д.)
- `nodeSummary` - доп. информация (напр. исполнитель трека, версия альбома, кол-во треков в плейлисте и т.д.)
- `nodeType` - тип ноды, либо значение из `com.badmanners.murglar.lib.core.model.node.NodeType`, либо свое для
  нестандартных типов
- `comparableId` - идентификатор, по которому хост-приложение будет сравнивать объекты при лайках/дизлайках (см. далее)
- `smallCoverUrl`, `bigCoverUrl` - ссылка на обложку/фото артиста/аватарку/etc.
- `serviceUrl` - ссылка на объект в музыкальном сервисе
- `nodeParameters` - объект `com.badmanners.murglar.lib.core.model.node.NodeParameters` с метаданными ноды:
    - `isDirectory` - папка это или трек
    - `pagingType` - тип пейджинга, если это папка
    - `hasSubdirectories` - могут ли в содержимом папки быть подпапки, если это папка
    - `isLikeable` - можно ли лайкнуть/дизлайкнуть этот элемент
    - `searchableContentNodeType` - тип контента, который будет искаться нодой, если это папка-'поисковая нода'
- `nodePath` - путь `com.badmanners.murglar.lib.core.model.node.Path` объекта в иерархии плагина

Базовые классы для треков/альбомов/артистов/плейлистов уже содержат свою реализацию `nodeName` и `nodeSummary`,
но если вам нужно кастомизировать отображение этой информации в хост-приложении, то можно переопределить эти поля.

### `NodeResolver` - точка входа и конфигурация навигации, поиска, обработки ссылок/лайков/событий

`com.badmanners.murglar.lib.core.node.NodeResolver` предоставляет плагину методы, при помощи которых хост-приложение
может взаимодействовать с плагином как с подобием файловой системы:

- получать корневые ноды, с которых начинается навигация (`getRootNodes`)
- получать ноды, их контент и связанные ноды (`getNode`, `getNodeContent`, `getRadioContent`, `getRelatedNodePaths`)
- получать информацию о том, какие списки (т.е. контент каких нод) являются списками лайкнутого контента,
  а также выполнять лайки/дизлайки (`supportsLikes`, `likesMapping`, `likeNode`)
- получать ноды по URL сервиса (`canGetNodeFromUrl`, `getNodeFromUrl`)
- отправлять сервису события, например начала/конца трека (`getNodeCustomActions`, `supportedEventsMapping`,
  `handleEvent`)
- перезагружать метаданные треков (`getTracksByMediaIds`)

Как заглушку на время разработки можно использовать `com.badmanners.murglar.lib.core.node.StubNodeResolver`.

Для основы реальной реализации стоит использовать `com.badmanners.murglar.lib.core.node.BaseNodeResolver`,
который реализует практически все методы, оставляя наследнику только определение конфигураций, описывающих контент
сервиса - см. `com.badmanners.murglar.lib.core.node.GenericConfiguration`,
`com.badmanners.murglar.lib.core.node.EntityConfiguration` и их наследников:

- `Root` - конф. корневой ноды (напр. 'Мои треки'/'Рекомендации'/etc), т.е. ноды, отображающейся на начальном экране:
  ```kotlin
  Root(
    pattern = "myTracks",
    name = messages::myTracks,
    paging = PAGEABLE,
    hasSubdirectories = false,
    nodeContentSupplier = ::getMyTracks
  ),
  Root(
    pattern = "recommendations",
    name = messages::recommendations,
    paging = NON_PAGEABLE,
    hasSubdirectories = true,
    nodeContentSupplier = ::getRecommendations
  )
  ```
- `Search` - конф. поисковой ноды, т.е. ноды, через которую можно осуществлять поиск по определенному типу контента
  (при помощи `NodeResolver.specifySearchableNode`):
  ```kotlin
  Search(
    pattern = "searchTracks",
    name = messages::tracksSearch,
    hasSubdirectories = false,
    contentType = TRACK,
    nodeContentSupplier = ::searchTracks
  )
  ```
- `Directory` - конф. для промежуточных папок, которые не являются моделями (т.е. не треками/альбомами/артистами/etc):
  ```kotlin
  Directory(
    pattern = "*/artist-<artistId>/popularTracks",
    paging = NON_PAGEABLE,
    hasSubdirectories = false,
    nodeContentSupplier = ::getArtistPopularTracks
  )
  ```
- `MappedEntity` - конф. для моделей, кроме треков:
  ```kotlin
  MappedEntity(
    pattern = "*/artist-<artistId>",
    paging = NON_PAGEABLE,
    hasSubdirectories = true,
    type = ARTIST,
    relatedPaths = ::getArtistRelatedPaths,
    like = LikeConfig(rootNodePath("myArtists"), ::likeArtist),
    urlPatterns = listOf("*sample.com/artist/<artistId:\\d+>*"),
    nodeSupplier = ::getArtist,
    nodeContentSupplier = ::getArtistSubdirectories
  ),
  MappedEntity(
    pattern = "*/album-<albumId>",
    paging = NON_PAGEABLE,
    hasSubdirectories = false,
    type = ALBUM,
    relatedPaths = ::getAlbumRelatedPaths,
    like = LikeConfig(rootNodePath("myAlbums"), ::likeAlbum),
    urlPatterns = listOf("*sample.com/album/<albumId:\\d+>*"),
    nodeSupplier = ::getAlbum,
    nodeContentSupplier = ::getAlbumTracks
  )
  ```
- `UnmappedEntity` - конфигурация, которая позволяет сделать кастомную обработку лайков/связанных нод/событий для
  моделей с альтернативными `NodeType`. Например, если у вас альбомы и подкасты это одна модель `SampleAlbum`,
  их пути формируются одинаковым образом (`*/album-<albumId>`), но у них разный `nodeType` и при этом
  лайкнутые альбомы и подкасты находятся в разных списках, то разделить лайки можно с помощью этой конфигурации:
  ```kotlin
  UnmappedEntity(
    type = PODCAST,
    like = LikeConfig(rootNodePath("myPodcasts"), ::likeAlbum)
  )
  ```
- `Track` - конфигурация трека, в том числе и для альтернативных `NodeType` (эпизод подкаста/глава аудиокниги/etc):
  ```kotlin
  Track(
    pattern = "*/track-<trackId>",
    type = TRACK,
    relatedPaths = ::getTrackRelatedPaths,
    like = LikeConfig(rootNodePath("myTracks"), ::likeTrack),
    urlPatterns = listOf(
        "*sample.com/album/<albumId:\\d+>/track/<trackId:\\d+>*",
        "*sample.com/track/<trackId:\\d+>*"
    ),
    events = listOf(
        EventConfig(TrackStart::class) { to<SampleTrack>().handleTrackStartEvent() },
        EventConfig(TrackEnd::class) { to<SampleTrack>().handleTrackEndEvent(it.endTimeMs) }
    ),
    nodeSupplier = ::getTrack
  ),
  Track(
    pattern = "*/podcast_episode-<trackId>",
    type = PODCAST_EPISODE,
    relatedPaths = ::getTrackRelatedPaths,
    nodeSupplier = ::getTrack
  )
  ```

На полный пример реализации можно глянуть в `com.badmanners.murglar.lib.sample.node.SampleNodeResolver`.

### `Path` и шаблоны путей

У каждой `Node` есть свой `Path`, который является путем ноды в `NodeResolver`.

Рассмотрим следующий пример, чтобы иметь представление, как формируются итоговые пути и осуществляется поиск
нужной конфигурации при помощи шаблонов (поле `pattern` в конфигурациях).

Так выглядит полный путь трека, открытого в нашем плагине из 'Моих артистов' -> 'Артист с id 123' -> 'Альбомы' ->
'Альбом с id 456' -> 'Трек с id 789':

```
sample/myArtists/artist-123/albums/album-456/track-789
```

Разберем каждый сегмент:

- `sample` - идентификатор плагина, используется для того, чтобы определить, какому плагину принадлежит путь.
  Указывать его при объявлении конфигураций **НЕ** нужно, `BaseNodeResolver` сделает это сам
- `myArtists` - `Root` конфигурация 'Мои артисты'
- `artist-123` - `MappedEntity` конфигурация артиста
- `albums` - `Directory` конфигурация подпапки артиста
- `album-456` - `MappedEntity` конфигурация альбома
- `track-789` - `Track` конфигурация трека

Когда полный путь ноды попадает в методы `getNode`, `getNodeParameters`, `getNodeContent` и
`getRadioContent`, реализация `BaseNodeResolver` начинает искать соответствующую конфигурацию при помощи шаблона из
поля `pattern` в конфигурациях и матчера шаблонов `com.badmanners.murglar.lib.core.utils.pattern.PatternMatcher`
(см. примеры и описание в его документации).

Для приведенного выше пути матчер найдет `Track` конфигурацию:

```
sample/myArtists/artist-123/albums/album-456/track-789   ==   */track-<trackId>
// при этом в аргументах матчера будет находится trackId=789
```

Аналогично и для всех родительских путей:

```
sample/myArtists/artist-123/albums/album-456   ==   */album-<albumId>
sample/myArtists/artist-123/albums             ==   */artist-<artistId>/albums
sample/myArtists/artist-123                    ==   */artist-<artistId>
sample/myArtists                               ==   myArtists
```

Таким образом при помощи шаблонов можно покрыть все возможные пути нод в плагине:

```
myTracks
myAlbums
myArtists
myPlaylists
myPodcasts
myAudiobooks
myHistoryTracks
recommendations
radio

searchTracks
searchAlbums
searchArtists
searchPlaylists
searchPodcasts
searchAudiobooks

*/track-<trackId>
*/podcast_episode-<trackId>
*/audiobook_part-<trackId>

*/album-<albumId>

*/artist-<artistId>/popularTracks
*/artist-<artistId>/albums
*/artist-<artistId>/compilations
*/artist-<artistId>/playlists
*/artist-<artistId>/similarArtists
*/artist-<artistId>

*/playlist_owner-<ownerId>_id-<playlistId>

*/type-<type>
*/radio_type-<radioType>_tag-<radioTag>
```

Все вышеперечисленное было лишь объяснением принципа работы и могло выглядеть сложным для реализации,
однако `BaseNodeResolver` уже содержит почти все необходимое для этого.

Изначально объекты моделей создаются в `Murglar`, но у них не заполнены поля `MutableNode.nodeParameters` и
`MutableNode.nodePath`, т.к. их значение зависит от контекста и места применения. Следовательно, нужно заполнить их
в реализации `NodeResolver` - для этого в `BaseNodeResolver` есть методы `convertXxx` для одиночных объектов и списков.

Рассмотрим на примере `Directory` конфигурации альбомов артиста:

```kotlin
Directory(
    pattern = "*/artist-<artistId>/albums",
    paging = NON_PAGEABLE,
    hasSubdirectories = true,
    nodeContentSupplier = ::getArtistAlbums
)

//...

fun getArtistAlbums(parentPath: Path, page: Int?, params: Map<String, String>): List<Node> =
    murglar.getArtistAlbums(params["artistId"]!!).convertAlbums(parentPath)
```

Здесь `murglar.getArtistAlbums` возвращает список альбомов `List<SampleAlbum>`,
а метод `convertAlbums` конвертирует их в список нод `List<Node>`,
заодно заполняя поля `nodeParameters` и `nodePath`, принимая в аргументах путь родительской папки `parentPath`.
На основе `parentPath` метод конвертации генерирует итоговый путь каждой `Node` и находит по этому пути
в конфигурациях соответствующие `NodeParameters`, сохраняя оба параметра в объект модели.

По умолчанию формируется путь следующего вида: `${parentPath}/${nodeType}-${nodeId}`.
Если нужно кастомизировать генерацию путей (например плейлист идентифицируется при помощи нескольких идентификаторов),
то это можно сделать при помощи собственных конвертеров:

```kotlin
fun playlistNodePath(parentPath: Path, playlist: SamplePlaylist): Path =
    parentPath.child("playlist_owner-${playlist.ownerId}_id-${playlist.id}")

fun List<SamplePlaylist>.convertSamplePlaylists(parentPath: Path) = convert(::playlistNodePath, parentPath)

fun SamplePlaylist.convertSamplePlaylist(parentPath: Path) = convert(::playlistNodePath, parentPath)
```

Итого для получения контента нужно реализовать `getXxx` методы и указать их в полях конфигураций `nodeSupplier`,
`nodeContentSupplier` и `nodeWithContentSupplier`.

### Поиск

Поиск реализуется при помощи `Search` конфигураций:

```kotlin
Search(
    pattern = "searchTracks",
    name = messages::tracksSearch,
    hasSubdirectories = false,
    contentType = TRACK,
    nodeContentSupplier = ::searchTracks
)

//...

fun searchTracks(parentPath: Path, page: Int?, params: Map<String, String>) =
    murglar.searchTracks(params.getQuery(), page!!).convertSampleTracks(parentPath)
```

Обратите внимание на `params.getQuery()` - этот метод позволяет получить поисковый запрос из параметров.

### Связанные ноды

У каждой `Node` могут быть связанные ноды - по сути это ссылки на другие ноды.
Например, для трека это могут быть ссылки на альбом/артиста/радио/etc:

```kotlin
fun getTrackRelatedPaths(node: Node): List<NamedPath> {
    val track = node.to<SampleTrack>()
    val paths = mutableListOf<NamedPath>()

    if (track.hasAlbum) {
        val albumPath = unmappedPath().child("album-${track.albumId}")
        paths += NamedPath(track.albumName!!, albumPath)
    }

    for (i in track.artistNames.indices) {
        val artistPath = unmappedPath().child("artist-${track.artistIds[i]}")
        paths += NamedPath(track.artistNames[i], artistPath)
    }

    if (node.nodeType == TRACK) {
        val radioPath = unmappedPath().child("radio_type-track_tag-${track.id}")
        paths += NamedPath("${messages.radio}: ${track.title}", radioPath)
    }

    return paths
}
```

Обратите внимание на `unmappedPath()` - этот метод возвращает специальный unmapped путь, который можно использовать
как родительский для нод, которые не являются контентом каких-то других нод.
Такими нодами являются связанные ноды, открытые по ссылке или перезагруженные по `mediaId`.

Также при создании путей связанных нод учитывайте, что они должны быть в том же формате, что и обычные ноды,
чтобы они могли быть найдены матчером по шаблонам из конфигураций, т.е. если у вас к примеру пути плейлистов
формируются как `playlist_owner-${playlist.ownerId}_id-${playlist.id}`, то и в связанных нодах они должны формироваться
в таком же виде.

Итого для поддержки связанных нод нужно реализовать `getXxxRelatedPaths` методы и указать их в поле
конфигураций `relatedPaths`.

Имейте в виду, что поиск конфигурации для обработчика связанных нод осуществляется по `NodeType`, а не по шаблону пути.

### Лайки

Лайки реализуются при помощи поля `like` конфигураций. Для этого нужно указать путь списка, который содержит лайки,
а также метод, который выполняет лайк/дизлайк:

```kotlin
MappedEntity(
    // ...
    type = NodeType.ALBUM,
    like = LikeConfig(rootNodePath("myAlbums"), ::likeAlbum),
    // ...
)

fun likeAlbum(node: Node, like: Boolean) {
    val album = node.to<SampleAlbum>()
    if (like)
        murglar.addAlbumToFavorite(album)
    else
        murglar.removeAlbumFromFavorite(album)
}
```

Имейте в виду, что поиск конфигурации для лайков осуществляется по `NodeType`, а не по шаблону пути.

### Открытие контента по URL

По умолчанию предполагается, что URL контента в сервисе имеет формат, из которого можно точно определить тип контента.
В таком случае достаточно лишь объявить список шаблонов URL для каждого типа контента:

```kotlin
Track(
    // ...
    urlPatterns = listOf(
        "*sample.com/album/<albumId:\\d+>/track/<trackId:\\d+>*",
        "*sample.com/track/<trackId:\\d+>*"
    ),
    nodeSupplier = ::getTrack
),
MappedEntity(
    // ...
    urlPatterns = listOf("*sample.com/album/<albumId:\\d+>*"),
    nodeSupplier = ::getAlbum,
),
MappedEntity(
    // ...
    urlPatterns = listOf("*sample.com/artist/<artistId:\\d+>*"),
    nodeSupplier = ::getArtist,
),
MappedEntity(
    // ...
    urlPatterns = listOf("*sample.com/users/<ownerId>/playlists/<playlistId:\\d+>*"),
    nodeSupplier = ::getPlaylist,
),
```

Если же тип контента заранее не известен, то можно либо переопределить методы `canGetNodeFromUrl` и `getNodeFromUrl`,
либо скомбинировать оба способа и использовать и шаблоны для форматов с известным типом, и кастомную обработку для
неизвестных:

```kotlin
override fun canGetNodeFromUrl(url: String): Boolean = url.contains("sample.page.link/")

override fun getNodeFromUrl(url: String): Node {
    val node = murglar.resolveUrlToNode(url)
    return node.convert(node.nodeType, unmappedPath())
}
```

```kotlin
override fun canGetNodeFromUrl(url: String): Boolean = when {
    url.contains("go.onelink.me/") -> true
    else -> super.canGetNodeFromUrl(url)
}

override fun getNodeFromUrl(url: String): Node {
    val finalUrl = when {
        url.contains("go.onelink.me/") -> murglar.resolveOneLinkMeUrlToServiceUrl(url)
        else -> url
    }
    return super.getNodeFromUrl(finalUrl)
}
```

### Обработка событий

Хост-приложение может присылать события, как из плеера (`com.badmanners.murglar.lib.core.model.event.PlayerEvent`),
так и кастомные (`com.badmanners.murglar.lib.core.model.event.CustomAction`), которые плагин может объявить,
переопределив метод `getNodeCustomActions`. Кастомные экшены будут доступны в меню ноды.

Обработать события можно при помощи поля `events` конфигураций:

```kotlin
Track(
    // ...
    events = listOf(
        EventConfig(TrackStart::class) { to<SampleTrack>().handleStartEvent() },
        EventConfig(TrackEnd::class) { to<SampleTrack>().handleEndEvent(it.endTimeMs) }
    ),
    // ...
)

fun SampleTrack.handleStartEvent() {
    murglar.reportTrackStart(this)
}
fun SampleTrack.handleEndEvent(endTimeMs: Int) {
    murglar.reportTrackEnd(this, endTimeMs)
}
```

Имейте в виду, что поиск конфигурации для обработчика событий осуществляется по `NodeType`, а не по шаблону пути.

## `Decryptor` - если сервис предоставляет зашифрованный контент

Если сервис предоставляет контент в зашифрованном виде, то для его расшифровки требуется реализовать
`com.badmanners.murglar.lib.core.decrypt.Decryptor` и передать его в конструктор `BaseMurglar`.

Пример реализации смотрите в `com.badmanners.murglar.lib.sample.decrypt.SampleDecryptor`.

## Тестирование

Для быстрой отладки/тестирования можно использовать CLI клиент из модуля `client-cli`, который представляет собой
примитивное окружение хост-приложения.

Также можно использовать реализации `Middleware` из модуля `client-cli` в юнит/интеграционных тестах, подключив модуль
как `testImplementation` зависимость в `<plugin_name>-core/build.gradle.kts`

## Android

Основные моменты создания Android модуля описаны в
[README основного проекта](https://github.com/badmannersteam/murglar-plugins) - разделы Installation и Usage.

Не забудьте добавить иконку сервиса и указать в AndroidManifest.xml шаблоны URL контента, если ваш плагин должен
поддерживать открытие контента по ссылкам.

Android модуль, настроенный Gradle плагином, уже содержит все необходимые Proguard правила для `core` библиотеки,
однако вам могут понадобиться дополнительные правила, если этого потребуют библиотеки, которые вы подключите.

## Сборка плагина

Вызовите `./gradlew clean build` и заберите готовые плагины:

- JAR (для десктопа) - `<plugin_name>-core/build/libs/murglar-plugin-<plugin_name>-<version>.jar`
- APK (для Android) - `<plugin_name>-android/build/outputs/apk/release/murglar-plugin-<plugin_name>-<version>.apk`

## Вопросы и предложения

Если у вас возникли вопросы, на которые в этом гайде, в документации кода либо в примерах нет ответов или у вас
есть предложения по улучшению этого гайда - обращайтесь:

- Telegram - https://t.me/murglardiscuss
- Почта - badmannersteam@gmail.com