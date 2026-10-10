package com.aiwa.bridge

/*
 * What to tell someone whose dictation does not go anywhere: the backend runs in Termux, and the app can neither install Termux nor
 * put the backend in it (one app cannot write into another). So the one line of bootstrap.sh is pasted once, by the person.
 * The app starts the backend when it is there; when it never comes up, the honest message is "it is not installed".
 */

/** The one line that installs (and later updates) the backend, run in Termux. */
const val BOOTSTRAP_COMMAND = "curl -fsSL https://raw.githubusercontent.com/theodoreyong9/Aiwa_store/main/android/backend/bootstrap.sh | bash"

/** Where Termux comes from: F-Droid or GitHub are its only official sources; the Play Store copy is an old unofficial one without RUN_COMMAND, which the widget needs. */
const val TERMUX_DOWNLOAD_URL = "https://f-droid.org/packages/com.termux/"

/** Starts Termux was asked for, without the backend ever answering, after which it is called not installed (not just slow). */
const val STARTS_BEFORE_MISSING = 2

/** What is wrong with a backend that does not answer. */
enum class BackendVerdict {
    /** Termux itself is not on the phone. */
    TERMUX_MISSING,

    /** Termux is there and was asked to start the backend several times, in vain: it is not installed in it (or Termux refuses). */
    NOT_INSTALLED,

    /** Not answering yet: worth another try. */
    DOWN,
}

fun backendVerdict(termuxInstalled: Boolean, startsWithoutAnswer: Int): BackendVerdict = when {
    !termuxInstalled -> BackendVerdict.TERMUX_MISSING
    startsWithoutAnswer >= STARTS_BEFORE_MISSING -> BackendVerdict.NOT_INSTALLED
    else -> BackendVerdict.DOWN
}

/** What the person is told (a toast, the app's red line); the widget's own line is shorter. */
fun backendProblemMessage(verdict: BackendVerdict): String = when (verdict) {
    BackendVerdict.TERMUX_MISSING ->
        "Termux n'est pas installé. Installe-le depuis F-Droid, puis colle dans Termux la ligne d'installation d'Aiwa (touche l'état du widget : elle est copiée)."
    BackendVerdict.NOT_INSTALLED ->
        "Le backend n'est pas installé dans Termux. Colle dans Termux la ligne d'installation d'Aiwa (touche l'état du widget : elle est copiée)."
    BackendVerdict.DOWN ->
        "Backend pas démarré — lancement automatique en cours, réessaie dans 10-15 secondes."
}
