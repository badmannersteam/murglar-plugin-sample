# Plugin system, description and examples.

This guide describes the entire process of creating the plugin.

Don't forget to look at the code of this example project, although it is written for a non-existent service and
only for the sake of an example, it is a full-fledged plugin in terms of code.
Also do not forget about the documentation in the code of the `core` module.

## General process of creating a plugin

1. Creating a project and declaring dependencies
2. Creating object models (tracks/albums/artists/playlists/etc)
3. Creating localization classes
4. Implementation of `Murglar` - main plugin interface, entry point
5. Implementation of `LoginResolver` - if the service supports authorization
6. Implementation of `NodeResolver` - service navigation, search, processing of links/likes/events
7. Implementation of `Decryptor` - if the service provides encrypted content
8. Building

## Creating a project, declaring Gradle plugins and dependencies

Described in the [README of main project](https://github.com/badmannersteam/murglar-plugins) -
Installation and Usage sections.

Keep in mind that the host application (which runs plugins) has in classpath core module with its
dependencies (`kotlin-stdlib`, `kotlinx-serialization-json`, `threetenbp`, `apache commons-text`) and implementations of
`NetworkMiddleware`, `PreferenceMiddleware`, `NotificationMiddleware` and `LoggerMiddleware`.

All other dependencies (e.g. protobuf/ftp-client/etc), if your plugin needs them, must be explicitly declared in the
`dependencies {...}` section of `<plugin_name>-core/build.gradle.kts`.

## Object models

All objects (tracks/albums/artists/playlists/radio/intermediate folders/etc) that will be accessible from outside
the plugin (i.e. are arguments/return values of its public methods) must be declared as classes, that are
inherit `com.badmanners.murglar.lib.core.model.node.Node`,
and have the annotation `@com.badmanners.murglar.lib.core.utils.contract.Model`, which creates a noarg constructor
(this is required to serialize these objects in host applications).

For more information about `Node` see
[Navigation in the service](#noderesolver---service-navigation-search-processing-of-urlslikesevents).

### Base classes

Use `BaseXxx` classes (which are descendants of `Node` and `MutableNode`) as ancestors:

- for podcast tracks/episodes/audiobook chapters/other audio - `com.badmanners.murglar.lib.core.model.track.BaseTrack`
- for albums - `com.badmanners.murglar.lib.core.model.album.BaseAlbum`
- for artists - `com.badmanners.murglar.lib.core.model.artist.BaseArtist`
- for playlists - `com.badmanners.murglar.lib.core.model.playlist.BasePlaylist`
- for radio - `com.badmanners.murglar.lib.core.model.radio.BaseRadio`
- for intermediate folders - `com.badmanners.murglar.lib.core.model.node.BaseNode`

### Type of model (`NodeType`)

Remember to specify the correct type of the object if it does not match the type in the ancestor class:

```kotlin
// like passing it in the constructor
@Model
class SampleTrack(
    //...
    override val nodeType: String
) : BaseTrack(/*...*/)

// or making it dependent on other fields
@Model
class SamplePlaylist(
    //...
    val isAlbum: Boolean
) : BasePlaylist(/*...*/) {
    override val nodeType = if (isAlbum) NodeType.ALBUM else NodeType.PLAYLIST
}
```

### Object fields and serialization

When designing a set of fields in objects, try to keep a balance:

- do not create fields for each attribute from the API service
- try to provide all the fields necessary to implement all the desired functionality,
  so that you don't have to recreate the cache in the host application because of a changed set of fields.

**NOT** declare `serialVersionUid` in objects - in case of deserialization errors the host application will re-request
all the necessary information.

## Messages and localization

The approach used for localization is to create an interface with key strings, and implementations of this
of the interface provide the real value in the desired localization. Changing localizations is transparently
accomplished using `com.badmanners.murglar.lib.core.localization.DelegatingMessages`
(see `com.badmanners.murglar.lib.core.service.BaseMurglar#messages`).

So, requirements for localization are following:

1. Create your own interface that inherits from `com.badmanners.murglar.lib.core.localization.Messages`
   (it contains basic keys that can also be used), and declare the necessary keys in it:
   ```kotlin
   interface SampleMessages : Messages {
       val loginHelpText: String
       val twoFAText: String
   } 
   ```
2. Create implementations for each localization (at least English (`DefaultXxx`) and Russian (`RuXxx`)), inheriting from
   the interface created in the previous paragraph, and `Messages` implementations in the corresponding language:
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
3. The created implementations will be used further in the constructor
   `com.badmanners.murglar.lib.core.service.BaseMurglar#localeToMessages` and can be used via the
   `com.badmanners.murglar.lib.core.service.BaseMurglar#messages` field.

## Murglar - plugin interface

The `com.badmanners.murglar.lib.core.service.Murglar` interface is the entry point through which the host application
interacts with the plugin.

As a base for implementation, you should use the `com.badmanners.murglar.lib.core.service.BaseMurglar` class, which
manages localization and provides `Messages` and basic `Middleware`s.

`Middleware`s are abstractions that allow the plugin to:

- `com.badmanners.murglar.lib.core.network.NetworkMiddleware` - execute http requests
    - `com.badmanners.murglar.lib.core.network.NetworkRequest` - request abstraction
    - `com.badmanners.murglar.lib.core.network.NetworkResponse` - response abstraction
    - `com.badmanners.murglar.lib.core.network.ResponseConverters` - standard string/byte/JSON response converters
- `com.badmanners.murglar.lib.core.preference.PreferenceMiddleware` - save settings/data between restarts
- `com.badmanners.murglar.lib.core.notification.NotificationMiddleware` -
  show notifications to the user (toasts/snackbars)
- `com.badmanners.murglar.lib.core.log.LoggerMiddleware` - execute logging

The `Middleware` implementations will be provided to plugin in the runtime by the host application.

### `Murglar` implementation

Create a class that inherits from `BaseMurglar`, using the previously created track class and message interface,
declare and pass to the constructor of `BaseMurglar`:

- plugin identifier
- icon URL (colored, 512px PNG)
- `Locale`->`Messages` mapping
- all `Middleware`s

This constructor will be called by the host application, for all `Middleware`s will be passed their implementations
and for `id: String` will be passed the value `murglarPlugin.id`/`murglarAndroidPlugin.id` from the `build.gradle.kts`,
depending on the platform.

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

### Plugin settings

The plugin can provide the following types of preferences to the user
(see `com.badmanners.murglar.lib.core.preference`):

- `ActionPreference` - call action (e.g. forced session/token update)
- `CopyPreference` - copy text (e.g. copy token/cookie/nickname)
- `EditPreference` - editing the value (e.g. changing the domain/API key)
- `ListPreference` - selection from a list
- `SwitchPreference` - switch

Settings declaration:

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

### List of available formats

This list allows user to select the default format for the player/downloader/etc.

Most music services provide a fixed set of available content formats. In cases, when the extension or bitrate is not
known in advance, you can use `Extension.UNKNOWN` and `Bitrate.B_UNKNOWN`.

The list must be declared in content quality descending order.
For example, for a service that offers the possibility to download the source track
(in a format and bitrate unknown in advance and bitrate), as well as 320 MP3 and 192 MP3, the list would look like this:

```kotlin
override val possibleFormats = listOf(
    Extension.UNKNOWN to Bitrate.B_UNKNOWN,
    Extension.MP3 to Bitrate.B_320,
    Extension.MP3 to Bitrate.B_192
)
```

### Lifecycle

The plugin has only one lifecycle method - `onCreate()`, which is called after the host application is started
and an `Murglar` instance is created.
In this method you can perform various actions if needed - for example, update session/tokens/keys.

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

### Content URL resolving

In most services, the content URL is not immediately available when the response from the API is converted to a model
or quickly becomes outdated. In such cases, the URL must be resolved later when needed using additional request(s).
The `resolveSourceForUrl` method performs this by getting as arguments a track object and a `Source`,
which should be returned with the already prepared content URL:

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

In cases where the server for some reason returned a URL for a different `Source` than the one it was requested for,
an exception must be thrown, otherwise the host application may behave incorrectly.

If the content URL is available immediately when the response from the API is converted to a model, and it can't become
outdated, then the URL can be immediately saved to `Source.url` at the time of creation,
and the `resolveSourceForUrl` method can return `Source` without modification.

### Lyrics

Song lyrics can be either simple or synchronized - see. `com.badmanners.murglar.lib.core.model.tag.Lyrics`.

If the service supports retrieving song lyrics, the `hasLyrics` method should be overridden:

```kotlin
override fun hasLyrics(track: SampleTrack) = track.hasLyrics 
```

The `hasLyrics` method must necessarily be non-blocking and executed quickly.
If it is not reliably known whether the lyrics exist, but it possible - it is better to return `true`.

To get the lyrics itself, the `getLyrics` method will be called to request the lyrics from the service:

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

Do not store text in a track object - this significantly increases the size of the object and decreases the
performance of the host application due to serialization/deserialization.

### Tags

Tags in the plugin are represented by the `com.badmanners.murglar.lib.core.model.tag.Tags` object.
It is the responsibility of the host application to implement the tagging itself and support different formats and
containers. The plugin is only required to populate this object with data in the `getTags` method:

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

There is a basic implementation of this method in `BaseMurglar`, you can override it for customization.
You can fill the tag object with data from the track model or by making additional requests to the service API.

### Reloading tracks from the service to restore the cache

In a host application there may be moments when there is track content in the cache, but there is no metadata (models)
for them, so the application can't display them.
For fast metadata retrieving there is a `getTracksByMediaIds` method that takes a list of MediaIds as input
(see `com.badmanners.murglar.lib.core.lib.core.model.track.BaseTrack#mediaId`
and `com.badmanners.murglar.lib.core.utils.MediaId`):

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

The method should return as many available track objects as possible from the service.
If the method cannot be implemented due to lack of the required API in the service, it should return an empty list.

### Implementation of main query methods

At this point, it remains to implement all the methods/queries that will perform the core functionality of the plugin:

- Retrieving of user's library
- Retrieving of objects and their contents (tracks, albums, playlists, artists, etc.)
- Search requests
- Methods for performing likes/dislikes
- Service notifications about track start/end events
- ...

See `com.badmanners.murglar.lib.sample.SampleMurglar` for an example implementation.

## `LoginResolver` - login into service

The service has an interface `com.badmanners.murglar.lib.core.login.LoginResolver` for login management
(see documentation), which allows the host application to get information about the current login status and
available login methods and perform login/logout.

If service does not support login - use `com.badmanners.murglar.lib.core.login.NoLoginResolver`.

`LoginResolver` supports 2 types of login, for each of them multiple variants can be declared:

1. with WebView (`webLogin` method) - the official service login page opens to the user, user
   logs in, detects from URLs/requests that the user has logged in, WebView cookies are used to get a
   token/cookie/session/etc.
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
2. with credentials (`credentialsLogin` method) - user enters email/login/password/token/cookie value/whatever,
   which allows make requests using this data for authorization
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

All variants will be displayed to the user by the host application.
The plugin only has to process them in the appropriate methods when the user selects one of them.
For processing example see `com.badmanners.murglar.lib.sample.login.SampleLoginResolver` and documentation of
`com.badmanners.murglar.lib.core.login.LoginResolver` and `com.badmanners.murglar.lib.core.login.CredentialLoginStep`.

Keep in mind that for login implementation you can use:

- `NetworkMiddleware`, which will contain all cookies from webview after the user logs in via webview,
  which allows:
    - perform requests from the context of a logged user (e.g. to get a permanent token).
    - extract tokens from cookies (if any)
- `PreferenceMiddleware`, into which tokens/logins/login by cookies status flag/etc can be stored

It is also possible to act in the opposite direction - if the service requires a set of cookies to perform a request,
then in case of web login it is enough to store login status flag in the `PreferenceMiddleware` (cookies after
login will be added from webview to `NetworkMiddleware`), and in case of credentials login it is enough to request from
the user the value of cookie, create a cookie, add it to `NetworkMiddleware`, check its relevance with some request,
that requires authorization, and store login status flag in the `PreferenceMiddleware`.

## `NodeResolver` - service navigation, search, processing of URLs/likes/events

### `Node` - abstraction for object models

All object models are inherited from the `com.badmanners.murglar.lib.core.model.node.Node` interface,
which is an abstraction that allows a unified representation of plugin content in the form of a hierarchy of folders
(albums/artists/playlists/folders/etc) and tracks.

Each `Node` contains:

- `nodeId` - element identifier
- `nodeName` - name (e.g. track/album/playlist/folder name, artist name, etc.)
- `nodeSummary` - additional information (e.g. track artist, album version, number of tracks in the playlist, etc.)
- `nodeType` - node type, either a value from `com.badmanners.murglar.lib.core.model.node.NodeType`
  or a custom one for non-standard types
- `comparableId` - identifier by which the host application will compare objects during likes/dislikes (see below)
- `smallCoverUrl`, `bigCoverUrl` - link to cover art/artist photo/avatar/etc.
- `serviceUrl` - link to the object in the music service
- `nodeParameters` - `com.badmanners.murglar.lib.core.model.node.NodeParameters` object with node metadata:
    - `isDirectory` - whether it is a folder or a track
    - `pagingType` - paging type, if it is a folder
    - `hasSubdirectories` - whether the contents of a folder can have subdirectories, if it is a folder
    - `isLikeable` - whether this element can be liked/disliked
    - `searchableContentNodeType` - the type of content to be searched by the node, if it is a 'search node' folder
- `nodePath` - `com.badmanners.murglar.lib.core.model.node.Path` of the object in the plugin hierarchy

The base classes for tracks/albums/artists/playlists already contain their own implementation of `nodeName`
and `nodeSummary`, but if you need to customize the displaying of this information in the host application,
you can override these fields.

### `NodeResolver` - entry point and configuration of navigation, search, URLs/likes/event processing

`com.badmanners.murglar.lib.core.node.NodeResolver` provides the plugin with methods by which the host application
can interact with the plugin as a file system semblance:

- get root nodes from which navigation starts (`getRootNodes`)
- get nodes, their content and related nodes (`getNode`, `getNodeContent`, `getRadioContent`, `getRelatedNodePaths`)
- get information about 'node type' -> 'node path' mapping for likes lists and perform likes/dislikes
  (`supportsLikes`, `likesMapping`, `likeNode`)
- get nodes by service URL (`canGetNodeFromUrl`, `getNodeFromUrl`)
- send events to the service, e.g. track start/end (`getNodeCustomActions`, `supportedEventsMapping`,
  `handleEvent`)
- reload track metadata (`getTracksByMediaIds`)

As a stub for development time, you can use `com.badmanners.murglar.lib.core.node.StubNodeResolver`.

As a base for real implementation you can use `com.badmanners.murglar.lib.core.node.BaseNodeResolver`,
which implements almost all methods, leaving to implementation only the declaration of the configurations,
that describing the content of the service - see `com.badmanners.murglar.lib.core.node.GenericConfiguration`,
`com.badmanners.murglar.lib.core.node.EntityConfiguration` and their successors:

- `Root` - configuration of the root node (e.g. 'My Tracks'/'Recommendations'/etc) -
  the node displayed on the start screen:
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
- `Search` - configuration of a searchable node - the node through which a specific content type can be searched for
  (using `NodeResolver.specifySearchableNode`):
  ```kotlin
  Search(
    pattern = "searchTracks",
    name = messages::tracksSearch,
    hasSubdirectories = false,
    contentType = TRACK,
    nodeContentSupplier = ::searchTracks
  )
  ```
- `Directory` - configuration for intermediate folders that are not models (not tracks/albums/artists/etc):
  ```kotlin
  Directory(
    pattern = "*/artist-<artistId>/popularTracks",
    paging = NON_PAGEABLE,
    hasSubdirectories = false,
    nodeContentSupplier = ::getArtistPopularTracks
  )
  ```
- `MappedEntity` - configuration for models other than tracks:
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
- `UnmappedEntity` - configuration that allows custom handling of likes/related nodes/events for
  models with alternative `NodeType`. For example, if albums and podcasts are the same model `SampleAlbum`,
  their paths are formed in the same way (`*/album-<albumId>`), but they have different `nodeType` and
  liked albums and podcasts are in different lists, you can separate the likes using this configuration:
  ```kotlin
  UnmappedEntity(
    type = PODCAST,
    like = LikeConfig(rootNodePath("myPodcasts"), ::likeAlbum)
  )
  ```
- `Track` - track configuration, also for the tracks with alternative `NodeType`
  (podcast episode/ audiobook chapter/etc):
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

For a complete example implementation, you can look at `com.badmanners.murglar.lib.sample.node.SampleNodeResolver`.

### `Path` and path templates

Each `Node` has its own `Path`, which is the path of the node in the `NodeResolver`.

Let's consider the following example to get an idea of how the final paths are generated
and how the search for the required configuration is performed using templates (`pattern` field in configurations).

This is how the full path of a track, opened in our plugin from 'My Artists' -> 'Artist with id 123' -> 'Albums' ->
'Album with id 456' -> 'Track with id 789', looks like:

```
sample/myArtists/artist-123/albums/album-456/track-789
```

Let's figure out each segment:

- `sample` - plugin identifier, used to determine which plugin the path belongs to.
  **Don't** specify it when declaring configurations, `BaseNodeResolver` will do it itself
- `myArtists` - `Root` configuration 'My Artists'
- `artist-123` - `MappedEntity` artist configuration
- `albums` - `Directory` artist subdirectory configuration
- `album-456` - `MappedEntity` album configuration
- `track-789` - `Track` track configuration

When the full path of a node is passed in the `getNode`, `getNodeParameters`, `getNodeContent` and
`getRadioContent` methods, the `BaseNodeResolver` implementation starts looking for the corresponding configuration
using the pattern from the `pattern` field in configurations and the pattern matcher
`com.badmanners.murglar.lib.core.utils.pattern.PatternMatcher` (see examples and description in its documentation).

For the above path, the matcher will find the `Track` configuration:

```
sample/myArtists/artist-123/albums/album-456/track-789   ==   */track-<trackId>
// trackId=789 will be presented in the matcher arguments
```

Similarly for all parent paths:

```
sample/myArtists/artist-123/albums/album-456   ==   */album-<albumId>
sample/myArtists/artist-123/albums             ==   */artist-<artistId>/albums
sample/myArtists/artist-123                    ==   */artist-<artistId>
sample/myArtists                               ==   myArtists
```

In this way, using templates, you can cover all possible node paths in the plugin:

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

All of the above was just an explanation of the principle and might look complicated to implement,
but `BaseNodeResolver` already contains almost everything needed for this.

Initially, model objects are created in `Murglar`, but they do not have the `MutableNode.nodeParameters` and
`MutableNode.nodePath` fields filled in, because their value depends on the context and location.
So it is necessary to fill them in `NodeResolver` implementation - for this purpose `convertXxx` methods
for single objects and lists are available in `BaseNodeResolver`.

Let's look at the `Directory` configuration of an artist's albums as an example:

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

Here, `murglar.getArtistAlbums` returns a list of albums `List<SampleAlbum>`,
and the `convertAlbums` method converts them into a list of nodes `List<Node>`,
at the same time filling in the `nodeParameters` and `nodePath` fields,
taking the path of the parent folder `parentPath` as arguments.
The conversion method generates the final path, based on the `parentPath`, of each `Node` and finds
in the configurations corresponding `NodeParameters`, storing both parameters in the model object.

By default, a path of the following form is generated: `${parentPath}/${nodeType}-${nodeId}`.
If you want to customize path generation (e.g. a playlist is identified by multiple identifiers),
this can be done using custom converters:

```kotlin
fun playlistNodePath(parentPath: Path, playlist: SamplePlaylist): Path =
    parentPath.child("playlist_owner-${playlist.ownerId}_id-${playlist.id}")

fun List<SamplePlaylist>.convertSamplePlaylists(parentPath: Path) = convert(::playlistNodePath, parentPath)

fun SamplePlaylist.convertSamplePlaylist(parentPath: Path) = convert(::playlistNodePath, parentPath)
```

So for querying content, we need to implement `getXxx` methods and specify them in the `nodeSupplier`,
`nodeContentSupplier` or `nodeWithContentSupplier` configuration fields.

### Search

The search is implemented using `Search` configurations:

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

Note `params.getQuery()` - this method retrieves the search query from the parameters.

### Related nodes

Each `Node` can have related nodes - essentially these are links to other nodes.
For example, for a track, these could be links to album/artist/radio/etc:

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

Note `unmappedPath()` - this method returns a special unmapped path that can be used
as a parent for nodes that are not content of any other nodes.
Such nodes are related nodes, opened by URL or reloaded by `mediaId`.

Also, when creating paths for related nodes, keep in mind that they should be in the same format as regular nodes,
so that they can be found by the matcher using templates from configurations, i.e. if your
playlist paths are formed as `playlist_owner-${playlist.ownerId}_id-${playlist.id}`,
then they should be formed for related nodes in the same way.

So for supporting related nodes you need to implement `getXxxRelatedPaths` methods and specify them in the field
`relatedPaths` field of configurations.

Note that the configuration lookup for the linked node handler is done by `NodeType`, not by path pattern.

### Likes

Likes are implemented using the `like` field of the configurations. This requires specifying the path of the list
that contains the likes and the method that performs the likes/dislikes:

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

Note that the configuration for likes is searched by `NodeType`, not by path pattern.

### Opening content by URL

By default, it is assumed that the URL of content in the service has a format from which the content type can
be determined. In such case all you need to do is declare a list of URL patterns for each content type:

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

If the content type is not known in advance, you can override the `canGetNodeFromUrl` and `getNodeFromUrl` methods
or combine both methods and use templates for formats with known type, and custom processing for unknown ones:

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

### Event handling

The host application can send events, both from the player (`com.badmanners.murglar.lib.core.model.event.PlayerEvent`),
and custom ones (`com.badmanners.murglar.lib.core.model.event.CustomAction`), which the plugin can declare,
by overriding the `getNodeCustomActions` method. The custom actions will be available in the node menu.

Events can be handled using the `events` field of the configurations:

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

Note that the configuration for the event handler is searched by `NodeType`, not by path pattern.

## `Decryptor` - if the service provides encrypted content

If the service provides content in encrypted form, decrypting it requires implementing
`com.badmanners.murglar.lib.core.decrypt.Decryptor` and pass it to the `BaseMurglar` constructor.

For an example implementation, see `com.badmanners.murglar.lib.sample.decrypt.SampleDecryptor`.

## Testing

For quick debugging/testing, you can use the CLI client from the `client-cli` module, which is a
primitive host application environment.

You can also use `Middleware` implementations from the `client-cli` module in the unit/integration tests by
declaring the module as a `testImplementation` dependency in `<plugin_name>-core/build.gradle.kts`.

## Android

The main points of Android module creation are described in
[README of the main project](https://github.com/badmannersteam/murglar-plugins) - sections Installation and Usage.

Don't forget to add a service icon and specify content URL templates in the AndroidManifest.xml if your plugin should
support opening content by URLs.

The Android module configured by the Gradle plugin already contains all the necessary Proguard rules
for the `core` library, but you may need additional rules if the libraries that you use require them.

## Plugin build

Call `./gradlew clean build` and grab the plugins:

- JAR (for desktop) - `<plugin_name>-core/build/libs/murglar-plugin-<plugin_name>-<version>.jar`
- APK (for Android) -`<plugin_name>-android/build/outputs/apk/release/murglar-plugin-<plugin_name>-<version>.apk`

## Questions and suggestions

If you have any questions that are not answered in this guide, in the code documentation or in the examples,
or if you have any suggestions for improving this guide, feel free to contact us:

- Telegram - https://t.me/murglardiscussen
- E-mail - badmannersteam@gmail.com