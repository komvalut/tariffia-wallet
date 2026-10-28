package io.horizontalsystems.walletkit.core

object TariffiaCdn {
    const val BASE_URL = "https://tariffia.app/cdn"

    fun url(path: String): String = "$BASE_URL/${path.trimStart('/')}"

    fun blockchainIcon(uid: String): String = url("blockchain-icons/32px/$uid@3x.png")

    fun coinIcon(uid: String): String = url("coin-icons/32px/$uid@3x.png")

    fun fiatIcon(code: String): String = url("fiat-icons/$code@3x.png")

    fun categoryIcon(uid: String): String = url("category-icons/$uid@3x.png")

    fun fundIcon(uid: String): String = url("fund-icons/$uid@3x.png")

    fun treasuryIcon(fundUid: String): String = url("treasury-icons/$fundUid@3x.png")

    fun auditorIcon(name: String): String = url("auditor-icons/$name@3x.png")

    fun headerImage(name: String): String = url("header-images/$name@3x.png")

    fun etfTreasury(ticker: String): String = url("etf-tresuries/$ticker@3x.png")
}
