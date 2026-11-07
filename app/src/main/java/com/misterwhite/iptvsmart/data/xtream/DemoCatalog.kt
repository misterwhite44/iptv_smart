package com.misterwhite.iptvsmart.data.xtream

/**
 * Service de démonstration : quelques vidéos libres de droits (films ouverts de la
 * Blender Foundation, flux HLS de test publics) pour montrer Zynora sans abonnement
 * IPTV. L'APK « Zynora IPTV Démo » (build -Pdemo=true) l'ouvre avec un bouton ;
 * dans l'appli normale il est caché et ne s'ouvre qu'en saisissant « demo » comme
 * serveur, identifiant et mot de passe (vérificateurs du Play Store).
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

    // Vidéos : copies publiques d'Internet Archive et serveur de téléchargement de Blender.
    // Images : extraites des films, servies par le site GitHub Pages de Zynora (docs/demo/).
    private const val ARCHIVE = "https://archive.org/download"
    private const val IMAGES = "https://misterwhite44.github.io/iptv_smart/demo"
    private const val BLENDER = "© Blender Foundation, sous licence Creative Commons Attribution (blender.org)."

    private class Film(
        val id: String,
        val name: String,
        val url: String,
        val image: String,
        val year: String,
        val duration: String,
        val genre: String,
        val plot: String,
    )

    private val films = listOf(
        Film(
            "1", "Big Buck Bunny", "$ARCHIVE/BigBuckBunny_124/Content/big_buck_bunny_720p_surround.mp4",
            "$IMAGES/bbb.jpg", "2008", "00:09:56", "Animation, Comédie",
            "Un lapin géant au grand cœur décide de donner une leçon à trois rongeurs qui s'en prennent aux animaux de la forêt.",
        ),
        Film(
            "2", "Elephants Dream", "$ARCHIVE/ElephantsDream/ed_hd_512kb.mp4",
            "$IMAGES/ed.jpg", "2006", "00:10:53", "Animation, Science-fiction",
            "Deux hommes explorent une machine immense et étrange, mais ils n'y voient pas du tout la même chose.",
        ),
        Film(
            "3", "Sintel", "$ARCHIVE/Sintel/sintel-2048-surround_512kb.mp4",
            "$IMAGES/sintel.jpg", "2010", "00:14:48", "Animation, Fantastique",
            "Une jeune femme parcourt des terres hostiles à la recherche du bébé dragon qu'elle a recueilli.",
        ),
        Film(
            "4", "Tears of Steel", "https://download.blender.org/demo/movies/ToS/tears_of_steel_720p.mov",
            "$IMAGES/tos.jpg", "2012", "00:12:14", "Science-fiction",
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
