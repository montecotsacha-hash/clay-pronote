package com.pronote.clay.core

/** Typed errors. The UI turns these into human sentences — a stack trace never reaches the user. */
sealed class PronoteError(val userMessage: String, val hint: String? = null) {
    object InvalidCredentials : PronoteError(
        "Impossible de se connecter à Pronote.",
        "Vérifie ton identifiant et ton mot de passe.",
    )
    object InvalidPronoteUrl : PronoteError(
        "L'adresse de ton établissement n'est pas valide.",
        "Exemple : https://0123456a.index-education.net/pronote/",
    )
    object NetworkUnavailable : PronoteError(
        "Aucune connexion internet.",
        "Tes dernières données restent consultables hors ligne.",
    )
    object SessionExpired : PronoteError(
        "Ta session Pronote a expiré.",
        "Reconnecte-toi pour repartir sur des données à jour.",
    )
    object PronoteUnavailable : PronoteError(
        "Pronote ne répond pas pour le moment.",
        "Réessaie dans quelques minutes.",
    )
    object CaptchaRequired : PronoteError(
        "Cet établissement demande une vérification supplémentaire.",
        "Ouvre Pronote dans un navigateur pour te connecter une fois, puis réessaie.",
    )
    object ServerError : PronoteError(
        "Le serveur de l'établissement a renvoyé une erreur.",
        "Ce n'est pas de ton côté — réessaie plus tard.",
    )
    object ParsingError : PronoteError(
        "Les données reçues n'ont pas pu être interprétées.",
        "Une mise à jour du connecteur est peut-être nécessaire.",
    )
    object IncompatibleConnector : PronoteError(
        "Pronote a modifié son système de connexion.",
        "Une mise à jour du connecteur est nécessaire. Tes données locales restent disponibles.",
    )
    object UnknownError : PronoteError("Une erreur inattendue est survenue.")
}

/** Minimal Result type so the repository never throws across layers. */
sealed class PronoteResult<out T> {
    data class Success<T>(val value: T) : PronoteResult<T>()
    data class Failure(val error: PronoteError) : PronoteResult<Nothing>()

    fun getOrNull(): T? = (this as? Success)?.value
    val isSuccess: Boolean get() = this is Success
}
