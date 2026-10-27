package com.misterwhite.iptvsmart.data.xtream

/**
 * Service de démonstration caché : quelques vidéos libres de droits (films ouverts
 * de la Blender Foundation, flux HLS de test publics) pour les testeurs et les
 * vérificateurs du Play Store. Rien ne l'affiche dans l'appli : il s'ouvre en saisissant
 * « demo » comme serveur, identifiant et mot de passe.
 * Aucune requête n'est envoyée à [SERVER] : XtreamClient répond avec ces données.
 */
object DemoCatalog {
    const val SERVER = "http://demo.zynora.invalid"
    const val USERNAME = "demo"
    const val PASSWORD = "demo"

    val credentials = XtreamCredentials(SERVER, USERNAME, PASSWORD)

    fun isDemo(credentials: XtreamCredentials) = credentials.serverUrl == SERVER

    /** Vrai si l'écran du fournisseur a été rempli avec les codes de la démo. */
    fun matchesLogin(server: String, username: String, password: String) =
        server.trim().equals(USERNAME, ignoreCase = true) &&
            username.trim() == USERNAME && password.trim() == PASSWORD

    private const val GTV = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample"
    private const val BLENDER = "© Blender Foundation, sous licence Creative Commons Attribution (blender.org)."

    private class Film(
        val id: String,
        val name: String,
        val file: String,
        val year: String,
        val duration: String,
        val genre: String,
        val plot: String,
    ) {
        val url get() = "$GTV/$file.mp4"
        val image get() = "$GTV/images/$file.jpg"
    }

    private val films = listOf(
        Film(
            "1", "Big Buck Bunny", "BigBuckBunny", "2008", "00:09:56", "Animation, Comédie",
            "Un lapin géant au grand cœur décide de donner une leçon à trois rongeurs qui s'en prennent aux animaux de la forêt.",
        ),
        Film(
            "2", "Elephants Dream", "ElephantsDream", "2006", "00:10:53", "Animation, Science-fiction",
            "Deux hommes explorent une machine immense et étrange, mais ils n'y voient pas du tout la même chose.",
        ),
        Film(
            "3", "Sintel", "Sintel", "2010", "00:14:48", "Animation, Fantastique",
            "Une jeune femme parcourt des terres hostiles à la recherche du bébé dragon qu'elle a recueilli.",
        ),
        Film(
            "4", "Tears of Steel", "TearsOfSteel", "2012", "00:12:14", "Science-fiction",
            "À Amsterdam, un groupe de scientifiques tente de sauver le monde d'une armée de robots.",
        ),
    )

    private class Channel(val id: String, val name: String, val url: String)

    private val channels = listOf(
        Channel("101", "Démo HLS 1", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"),
        Channel(
            "102", "Démo HLS 2",
            "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8",
        ),
        Channel(
            "103", "Démo Tears of Steel",
            "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8",
        ),
    )

    private const val SERIES_ID = "201"
    private const val EPISODE_OFFSET = 300

    val account = AccountInfo(
        status = "Active",
        expiresAtMillis = null,
        maxConnections = 1,
        activeConnections = 0,
        isTrial = false,
        allowedOutputFormats = listOf("m3u8"),
    )

    fun categories(type: ContentType): List<Category> = listOf(
        when (type) {
            ContentType.LIVE -> Category("1", "Chaînes de démonstration")
            ContentType.MOVIE -> Category("2", "Films libres de droits")
            ContentType.SERIES -> Category("3", "Séries libres de droits")
        }
    )

    val liveChannels: List<LiveChannel>
        get() = channels.map { LiveChannel(it.id, it.name, logo = null, categoryId = "1", epgChannelId = null) }

    val movies: List<Movie>
        get() = films.map {
            Movie(it.id, it.name, it.image, rating = null, addedAt = 0L, categoryId = "2", extension = "mp4")
        }

    private val anthology = Series(
        id = SERIES_ID,
        name = "Les films ouverts Blender",
        cover = films.first().image,
        backdrop = films[2].image,
        plot = "Les courts métrages ouverts de la Blender Foundation, un par épisode. $BLENDER",
        genre = "Animation",
        year = "2006",
        rating = null,
        updatedAt = 0L,
        categoryId = "3",
    )

    val series: List<Series> get() = listOf(anthology)

    fun movieDetails(movie: Movie): MovieDetails {
        val film = films.firstOrNull { it.id == movie.id }
        return MovieDetails(
            movie = movie,
            backdrop = film?.image,
            plot = film?.let { "${it.plot} $BLENDER" },
            cast = null,
            director = "Blender Foundation",
            genre = film?.genre,
            releaseDate = film?.year,
            duration = film?.duration,
        )
    }

    fun seriesDetails(series: Series): SeriesDetails = SeriesDetails(
        series = series,
        cast = null,
        seasons = mapOf(
            1 to films.mapIndexed { index, film ->
                Episode(
                    id = (EPISODE_OFFSET + film.id.toInt()).toString(),
                    season = 1,
                    number = index + 1,
                    title = film.name,
                    extension = "mp4",
                    image = film.image,
                    plot = film.plot,
                    durationSecs = null,
                )
            }
        ),
    )

    /** Adresse réelle de la vidéo pour un identifiant de chaîne, de film ou d'épisode. */
    fun streamUrl(id: String): String {
        channels.firstOrNull { it.id == id }?.let { return it.url }
        val filmId = id.toIntOrNull()?.let { if (it > EPISODE_OFFSET) it - EPISODE_OFFSET else it }?.toString()
        return (films.firstOrNull { it.id == filmId } ?: films.first()).url
    }
}
