package tp.content.gameframe

import tp.plugin.AttrKey
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.awaitCount
import tp.plugin.awaitPauseButton
import tp.symbols.Components
import tp.symbols.Interfaces
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * The bank PIN (docs/mechanics/bank-pin.md): the keypad (`bankpin_keypad` 213) and the settings (`bankpin_settings` 14).
 * The client draws, shuffles and collects the keypad itself (client scripts 333 / 653 / 679 / 685) and answers with
 * one count-dialog resume: the four digits as a number, [EXIT] or [FORGOTTEN] (script 686). Security-critical: every
 * answer is range-checked, lockouts and delays run on real time and persist across logout (BP4, Wiki changes 2015).
 */
internal object BankPin {
    /** BP2: unlocked for this session (never saved). */
    private val UNLOCKED = AttrKey<Boolean>("bankpin_unlocked")

    private const val HASH = "bankpin_hash"
    private const val PENDING_HASH = "bankpin_pending_hash"
    private const val PENDING_AT = "bankpin_pending_at"
    private const val DELETE_AT = "bankpin_delete_at"
    private const val DELAY_DAYS = "bankpin_delay_days"
    private const val ALWAYS_LOCK = "bankpin_always_lock"
    private const val FAILS = "bankpin_fails"
    private const val LOCKED_UNTIL = "bankpin_locked_until"
    private const val GRACE_UNTIL = "bankpin_grace_until"
    private const val GRACE_ADDRESS = "bankpin_grace_address"

    /** Script 686 answers for the keypad's "Exit" and "I don't know it." buttons (interface 213 components 13 / 15). */
    private const val EXIT = 12345
    private const val FORGOTTEN = 54321

    /** BP1: a PIN is four digits, so the client's number is 0..9999. */
    private const val MAX_PIN = 9999
    private const val DIGITS = 4
    private const val RADIX = 10

    /** BP5: recovery delays offered (Wiki "Bank PIN": "either 3 or 7 days"); 7 is the default. */
    private const val SHORT_DELAY_DAYS = 3L
    private const val LONG_DELAY_DAYS = 7L
    private const val SECONDS_PER_DAY = 86_400L

    /** BP3: "stay unlocked for 5 minutes after logging out" (Wiki "Bank PIN", 9 March 2017). */
    private const val GRACE_SECONDS = 300L

    /** BP4 (Wiki "Bank PIN"): 2nd failure 10 s, 3rd 15 s, 4th and later 10 minutes. */
    private const val FIRST_LOCK_FAILS = 2
    private const val FIRST_LOCK_SECONDS = 10L
    private const val SECOND_LOCK_FAILS = 3
    private const val SECOND_LOCK_SECONDS = 15L
    private const val LONG_LOCK_SECONDS = 600L

    private const val MESSAGE_REMINDER =
        "Customers are reminded that they should NEVER tell anyone their Bank PINs or passwords, nor should they ever " +
            "enter their PINs on any website form."

    // ---- state

    private enum class State { NONE, PENDING, ACTIVE }

    private fun PlayerRef.state(): State =
        when {
            saved(HASH) != null -> State.ACTIVE
            saved(PENDING_HASH) != null -> State.PENDING
            else -> State.NONE
        }

    private fun PlayerRef.delayDays(): Long = saved(DELAY_DAYS) ?: LONG_DELAY_DAYS

    /** BP5 / BP6: a pending PIN becomes active, a requested deletion happens, once its real-time delay has passed. */
    fun settle(p: PlayerRef) {
        val now = p.epochSeconds
        val pendingAt = p.saved(PENDING_AT)
        if (pendingAt != null && now >= pendingAt) {
            p.setSaved(HASH, p.saved(PENDING_HASH))
            p.setSaved(PENDING_HASH, null)
            p.setSaved(PENDING_AT, null)
            p.message("Your Bank PIN is now active.")
        }
        val deleteAt = p.saved(DELETE_AT)
        if (deleteAt != null && now >= deleteAt) {
            p.setSaved(HASH, null)
            p.setSaved(DELETE_AT, null)
            p.message("Your Bank PIN has now been deleted.")
        }
    }

    /** BP3: at login, the PIN stays unlocked when the player left within 5 minutes from the same place. */
    fun onLogin(p: PlayerRef) {
        settle(p)
        val until = p.saved(GRACE_UNTIL)
        val sameAddress = p.saved(GRACE_ADDRESS) == p.loginAddressHash
        if (until != null && p.epochSeconds < until && sameAddress) p[UNLOCKED] = true
        p.setSaved(GRACE_UNTIL, null)
        p.setSaved(GRACE_ADDRESS, null)
    }

    fun onLogout(p: PlayerRef) {
        if (p[UNLOCKED] != true || p.saved(HASH) == null || p.saved(ALWAYS_LOCK) == 1L) return
        p.setSaved(GRACE_UNTIL, p.epochSeconds + GRACE_SECONDS)
        p.setSaved(GRACE_ADDRESS, p.loginAddressHash)
    }

    // ---- entering the PIN

    /** BP2: true once the PIN was entered this session (asking for it now if needed), or when there is no PIN. */
    suspend fun PlayerScript.unlock(): Boolean {
        val p = player
        settle(p)
        if (p.saved(HASH) == null || p[UNLOCKED] == true) return true
        val wait = (p.saved(LOCKED_UNTIL) ?: 0L) - p.epochSeconds
        if (wait > 0) {
            p.message("You must wait ${waitText(wait)} before trying your Bank PIN again.")
            return false
        }
        val answer = keypad("Bank of Gielinor") ?: return false
        p.closeModal()
        return checkEntered(p, answer)
    }

    /** BP2 / BP4: the keypad's answer: "I don't know it." requests deletion, a wrong PIN counts as a failure. */
    private fun checkEntered(p: PlayerRef, answer: Int): Boolean {
        val ok = answer != FORGOTTEN && hash(p, answer) == p.saved(HASH)
        when {
            answer == FORGOTTEN -> {
                requestDeletion(p)
            }

            !ok -> {
                failed(p)
            }

            else -> {
                p.setSaved(FAILS, null)
                p.setSaved(LOCKED_UNTIL, null)
                p[UNLOCKED] = true
                // OPEN-BP3: entering the PIN cancels a deletion request (RS2's recovery rule), so a thief's request fails.
                if (p.saved(DELETE_AT) != null) {
                    p.setSaved(DELETE_AT, null)
                    p.message("Your request to delete your Bank PIN has been cancelled.")
                }
            }
        }
        return ok
    }

    private fun failed(p: PlayerRef) {
        val fails = (p.saved(FAILS) ?: 0L) + 1
        p.setSaved(FAILS, fails)
        val lock =
            when {
                fails < FIRST_LOCK_FAILS -> 0L
                fails == FIRST_LOCK_FAILS.toLong() -> FIRST_LOCK_SECONDS
                fails == SECOND_LOCK_FAILS.toLong() -> SECOND_LOCK_SECONDS
                else -> LONG_LOCK_SECONDS
            }
        if (lock > 0) p.setSaved(LOCKED_UNTIL, p.epochSeconds + lock)
        p.message("The PIN you entered was incorrect.")
        if (fails == FIRST_LOCK_FAILS.toLong()) {
            p.message("If you have made a mistake, use the 'Exit' button and start again.")
        }
    }

    private fun waitText(seconds: Long): String =
        if (seconds >= SECONDS_PER_MINUTE) "${(seconds + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE} minutes" else "$seconds seconds"

    private const val SECONDS_PER_MINUTE = 60L

    /**
     * Opens the keypad titled [title] and waits for the client's answer: a PIN 0..9999 or [FORGOTTEN]; null for "Exit",
     * a closed keypad or anything out of range (a hostile client).
     */
    private suspend fun PlayerScript.keypad(title: String): Int? {
        val p = player
        SideInventory.openMain(p, Interfaces.BANKPIN_KEYPAD)
        p.ifText(Components.Bankpin_Keypad.TITLE, title)
        val answer = awaitCount() ?: return null
        if (answer == EXIT) p.closeModal()
        return answer.takeIf { it in 0..MAX_PIN || it == FORGOTTEN }
    }

    private fun requestDeletion(p: PlayerRef) {
        if (p.saved(HASH) == null) return
        if (p.saved(DELETE_AT) == null) p.setSaved(DELETE_AT, p.epochSeconds + p.delayDays() * SECONDS_PER_DAY)
        p.message("Your Bank PIN will be deleted in ${daysLeft(p, DELETE_AT)}.")
    }

    // ---- settings

    /** BP5-BP8: the settings panel (bankers' "I'd like to check my PIN settings."); an active PIN is asked for first. */
    suspend fun PlayerScript.settings() {
        val p = player
        if (!unlock()) return
        while (true) {
            showSettings(p)
            val click = awaitPauseButton() ?: return
            if (!settingsClick(click)) return
        }
    }

    private fun showSettings(p: PlayerRef) {
        settle(p)
        SideInventory.openMain(p, Interfaces.BANKPIN_SETTINGS)
        p.ifHide(Components.Bankpin_Settings.SETTINGS, false)
        p.ifHide(Components.Bankpin_Settings.WARNING, true)
        val state = p.state()
        p.ifText(
            Components.Bankpin_Settings.STATUSOUTPUT,
            when {
                state == State.PENDING -> "PIN coming in ${daysLeft(p, PENDING_AT)}"
                p.saved(DELETE_AT) != null -> "PIN deleted in ${daysLeft(p, DELETE_AT)}"
                state == State.ACTIVE -> "You have a PIN"
                else -> "No PIN set"
            },
        )
        p.ifText(Components.Bankpin_Settings.DELAYOUTPUT, "${p.delayDays()} days")
        p.ifText(Components.Bankpin_Settings.LOGOUTOUTPUT, if (p.saved(ALWAYS_LOCK) == 1L) "Always lock" else "Stay unlocked for 5 mins")
        p.ifText(Components.Bankpin_Settings.MESSAGE, MESSAGE_REMINDER)
        p.ifHide(Components.Bankpin_Settings.BUTTONS_NOPIN, state != State.NONE)
        p.ifHide(Components.Bankpin_Settings.BUTTONS_PENDINGPIN, state != State.PENDING)
        p.ifHide(Components.Bankpin_Settings.BUTTONS_HASPIN, state != State.ACTIVE)
        for (c in BUTTONS) p.ifEvents(c, -1..-1, tp.plugin.IfEvents.PAUSE_BUTTON)
    }

    /** One settings button; false closes the panel. Every button checks the state it belongs to (hostile clicks). */
    private suspend fun PlayerScript.settingsClick(component: Int): Boolean {
        val p = player
        val state = p.state()
        return when (component) {
            Components.Bankpin_Settings.SET -> {
                state != State.NONE || setPin(pending = true)
            }

            Components.Bankpin_Settings.CHANGE -> {
                state != State.ACTIVE || setPin(pending = false)
            }

            Components.Bankpin_Settings.DELETE -> {
                state != State.ACTIVE || deletePin()
            }

            Components.Bankpin_Settings.DELAY0, Components.Bankpin_Settings.DELAY1 -> {
                if (state != State.PENDING) p.setSaved(DELAY_DAYS, if (p.delayDays() == LONG_DELAY_DAYS) SHORT_DELAY_DAYS else null)
                true
            }

            Components.Bankpin_Settings.LOGOUTHANDLER -> {
                if (state == State.ACTIVE) p.setSaved(ALWAYS_LOCK, if (p.saved(ALWAYS_LOCK) == 1L) null else 1L)
                true
            }

            Components.Bankpin_Settings.CANCEL -> {
                if (state == State.PENDING) {
                    p.setSaved(PENDING_HASH, null)
                    p.setSaved(PENDING_AT, null)
                    p.message("The PIN has been cancelled.")
                }
                true
            }

            else -> {
                false
            }
        }
    }

    /** BP5 / BP7: a new PIN entered twice; a first PIN waits for the recovery delay, a changed one applies at once. */
    private suspend fun PlayerScript.setPin(pending: Boolean): Boolean {
        val p = player
        val first = keypad("Choose a new PIN")?.takeIf { it <= MAX_PIN } ?: return false
        if (!allowed(first)) {
            p.closeModal()
            p.message("That PIN is too easy to guess. Please choose a different one.")
            return false
        }
        val second = keypad("Confirm your new PIN")?.takeIf { it <= MAX_PIN } ?: return false
        if (second != first) {
            p.message("The PINs you entered do not match.")
            return true
        }
        if (pending) {
            p.setSaved(PENDING_HASH, hash(p, first))
            p.setSaved(PENDING_AT, p.epochSeconds + p.delayDays() * SECONDS_PER_DAY)
            p.message("Your Bank PIN will become active in ${daysLeft(p, PENDING_AT)}.")
        } else {
            p.setSaved(HASH, hash(p, first))
            p.message("Your Bank PIN has been changed.")
        }
        return true
    }

    /** BP6: deleting waits for the recovery delay (Wiki); asked once more on the panel's warning layer. */
    private suspend fun PlayerScript.deletePin(): Boolean {
        val p = player
        val deleting = p.saved(DELETE_AT) != null
        // The client ignores pause buttons after one until the panel is sent again (it waits for the server's answer).
        showSettings(p)
        p.ifText(
            Components.Bankpin_Settings.WARNINGQUESTION,
            if (deleting) "Do you want to keep your Bank PIN?" else "Do you really want to delete your Bank PIN?",
        )
        p.ifText(Components.Bankpin_Settings.WARNING_YES, "Yes")
        p.ifText(Components.Bankpin_Settings.WARNING_NO, "No")
        p.ifHide(Components.Bankpin_Settings.WARNING, false)
        val click = awaitPauseButton() ?: return false
        if (click != Components.Bankpin_Settings.WARNING_YES) return true
        if (deleting) {
            p.setSaved(DELETE_AT, null)
            p.message("Your Bank PIN will not be deleted.")
        } else {
            requestDeletion(p)
        }
        return true
    }

    /** BP1 (Wiki): four equal digits and runs up or down by one (3456, 8765) are refused. */
    private fun allowed(pin: Int): Boolean {
        val d = IntArray(DIGITS) { i -> pin / pow10(DIGITS - 1 - i) % RADIX }
        val steps = (1 until DIGITS).map { d[it] - d[it - 1] }.toSet()
        return steps.size != 1 || steps.first() !in -1..1
    }

    private fun pow10(n: Int): Int = (0 until n).fold(1) { acc, _ -> acc * RADIX }

    private fun daysLeft(p: PlayerRef, key: String): String {
        val days = (((p.saved(key) ?: 0L) - p.epochSeconds + SECONDS_PER_DAY - 1) / SECONDS_PER_DAY).coerceAtLeast(1)
        return if (days == 1L) "1 day" else "$days days"
    }

    /**
     * The PIN is kept as a hash salted with the account id (the name without a hub), never as the digits: four digits
     * cannot resist a search, but the save no longer shows the PIN to whoever reads it.
     */
    private fun hash(p: PlayerRef, pin: Int): Long {
        val salt = if (p.accountId != 0L) p.accountId.toString() else p.username.lowercase()
        val md = MessageDigest.getInstance("SHA-256")
        md.update("bankpin:$salt:$pin".toByteArray(Charsets.UTF_8))
        return ByteBuffer.wrap(md.digest()).long
    }

    private val BUTTONS =
        listOf(
            Components.Bankpin_Settings.SET,
            Components.Bankpin_Settings.CHANGE,
            Components.Bankpin_Settings.DELETE,
            Components.Bankpin_Settings.DELAY0,
            Components.Bankpin_Settings.DELAY1,
            Components.Bankpin_Settings.LOGOUTHANDLER,
            Components.Bankpin_Settings.CANCEL,
            Components.Bankpin_Settings.WARNING_YES,
            Components.Bankpin_Settings.WARNING_NO,
        )
}
