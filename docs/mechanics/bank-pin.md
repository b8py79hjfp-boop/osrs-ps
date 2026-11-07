# Bank PIN

Implementation: `content/gameframe/.../BankPin.kt` (main session: security and persistence). Interfaces `bankpin_keypad` (213) and
`bankpin_settings` (14). Sources: OSRS Wiki "Bank PIN" (rules, lockouts, delays, 2015/2017/2018 changes), "Transcript:Banker"
(dialogue), client scripts 333 / 653 / 679 / 684 / 685 / 686 (keypad), interface 14 / 213 hooks and default texts (`cache-dump interface`).

## Rules

| # | Rule | Source |
|---|---|---|
| BP1 | A PIN is four digits. Four equal digits and runs up or down by one (3456, 8765) are refused. | Wiki "Choosing a PIN" |
| BP2 | With an active PIN, opening the bank (booths, chests, "Bank" ops, bankers, `OpenBankRequest`, `::openbank`), the Grand Exchange ("Exchange") and the PIN settings asks for it once; it then stays unlocked until logout. | Wiki "Using a bank PIN" |
| BP3 | The PIN stays unlocked for 5 minutes after logout when the "logout behaviour" setting allows it (the default for new accounts) and the next login comes from the same address (compared as a hash). | Wiki 9 March 2017, 20 September 2018 |
| BP4 | Failures: the 2nd wrong entry locks the keypad for 10 s (and reminds the player of "Exit"), the 3rd for 15 s, the 4th and later for 10 minutes. The count and the lock are real time and survive logout; a correct entry resets them. | Wiki; Wiki 16 April 2015 |
| BP5 | Setting a first PIN: entered twice, then pending for the recovery delay (3 or 7 days, 7 by default), shown as "PIN coming in N days"; the pending PIN can be cancelled. | Wiki "Obtaining a bank PIN" |
| BP6 | Deleting ("Delete your PIN" after a Yes/No warning, or the keypad's "I don't know it.") happens after the recovery delay. | Wiki "Removing a bank PIN" |
| BP7 | "Change your PIN" (PIN already entered this session): the new PIN twice, effective at once. | OPEN-BP2 |
| BP8 | The keypad is client-side: script 653 shuffles the ten buttons (20 random swaps) and script 679 places each digit at a random spot inside its box; script 685 builds the number and answers with one RESUME_P_COUNTDIALOG; "Exit" answers 12345 and "I don't know it." 54321 (script 686). The server opens 213, sets its title and range-checks the answer (0..9999 or 54321). | CS 333, 653, 679, 685, 686 |

## Tick timeline

Opening a gated feature: op -> keypad modal opens in the same script -> the client's count resume arrives in a later client-input
phase and resumes the script -> keypad closes and the feature opens in that same tick. A closed keypad (walking away) ends the script
(`closeModal` drops the input wait).

## OPEN

- **OPEN-BP1** Wording of every game message and of the settings outputs ("PIN coming in N days", "Stay unlocked for 5 mins", ...);
  the "Messages" box text is the familiar Jagex reminder, unverified for rev 242. Keypad titles are ours.
- **OPEN-BP2** Whether "Change your PIN" and "Delete your PIN" are immediate or delayed when the PIN is known; the Wiki only says deletion takes 3 or 7 days.
- **OPEN-BP3** A correct entry cancels a pending deletion (RS2's recovery rule, protects against a hijacker's "I don't know it.").
- **OPEN-BP4** The 20 extra bank slots for having a PIN (Wiki "Advantages") are not added; bank capacity stays at B3.
- **OPEN-BP5** Other PIN-protected features (Wiki list: seed vault, STASH, NMZ coffer, Blast Furnace coffer, Tool Leprechaun, clan
  settings, POH building mode, ...) are not gated yet; content can call `BankPin.unlock` (gameframe) or should get a plugin-api hook
  when the first one outside gameframe needs it.
- **OPEN-BP6** `bankmain:locks` (the padlock in the bank's bottom bar, ops "*") is not handled.
- **OPEN-BP7** The "buy more bank slots" banker option (OPEN-B1).
