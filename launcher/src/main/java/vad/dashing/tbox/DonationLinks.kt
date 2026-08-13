package vad.dashing.tbox

/**
 * Donation channels shown in the About console («О лаунчере»).
 *
 * An empty list hides the section entirely. Fill in real payment URLs to enable it —
 * each entry renders as a QR (scannable from a phone) plus a copyable link.
 */
object DonationLinks {
    data class Entry(
        /** Channel name, e.g. "Boosty", "ЮMoney", "CloudTips". */
        val title: String,
        /** Short hint shown under the title, e.g. "разовый перевод" / "подписка". */
        val subtitle: String,
        /** Payment URL opened on tap and encoded into the QR code. */
        val url: String,
    )

    val entries: List<Entry> = listOf(
        Entry(
            title = "Т-Банк — сбор средств",
            subtitle = "разовый перевод любой суммы",
            url = "https://www.tbank.ru/cf/jle4TITNel",
        ),
    )

    /** Feedback channels (not donations) shown in the About console. */
    val feedbackEntries: List<Entry> = listOf(
        Entry(
            title = "Telegram-бот",
            subtitle = "обратная связь / инструкция по настройке",
            url = "https://t.me/Dashing_Club_Launcher_bot",
        ),
    )
}
