package kr.ac.suwon.arsagent.model

enum class Screen { Home, Detail, Purpose, Call, Ended }
enum class PhoneTab { Calls, Contacts, Keypad }
enum class Phase { Dialing, Listening, Selecting, ListeningResult, Done, Choice, Answer, Secure, Sending, WaitingAgent }
enum class AudioRoute { Phone, Speaker }
enum class DemoScenario(val title: String) {
    Weather("날씨 안내"), Choice("후보 선택"), Answer("추가 답변"), Secure("민감정보 입력"), Handoff("상담원 연결")
}

class SecureEntry private constructor(private val digits: String) {
    val count: Int get() = digits.length
    fun append(digit: Char) = if (digit in '0'..'9' && count < 6) SecureEntry(digits + digit) else this
    fun delete() = SecureEntry(digits.dropLast(1))
    override fun toString(): String = "[redacted]"
    companion object { val Empty = SecureEntry("") }
}

class PhoneNumber(raw: String) {
    val digits = raw.trim().replace("-", "").replace(" ", "")
    val canCall: Boolean = digits.length in 3..13 && digits.all { it in '0'..'9' }
    val canUseAgent: Boolean = canCall && !digits.startsWith("010")
}

sealed interface Action {
    data class Dial(val number: String) : Action
    data class GoalChanged(val text: String) : Action
    data class AnswerChanged(val text: String) : Action
    data class Key(val digit: Char) : Action
    data class Tab(val tab: PhoneTab) : Action
    data class Detail(val number: String) : Action
    data class Scenario(val scenario: DemoScenario) : Action
    data class Select(val key: String) : Action
    data class Route(val route: AudioRoute) : Action
    data object StartAgent : Action
    data object StartNormal : Action
    data object CommitGoal : Action
    data object CommitAnswer : Action
    data object DeleteSecure : Action
    data object CommitSecure : Action
    data object Back : Action
    data object End : Action
    data object Tick : Action
    data object DemoAdvance : Action
    data object ToggleAudio : Action
    data object ToggleAudioMenu : Action
    data object ToggleMute : Action
    data object ToggleHold : Action
    data object OpenKeypad : Action
    data object DeleteDial : Action
    data object ClearDial : Action
}

data class AppState(
    val screen: Screen = Screen.Home,
    val tab: PhoneTab = PhoneTab.Keypad,
    val number: String = "",
    val agent: Boolean = false,
    val phase: Phase = Phase.Dialing,
    val goal: String = "",
    val goalDraft: String = "",
    val answerDraft: String = "",
    val secureEntry: SecureEntry = SecureEntry.Empty,
    val elapsedSeconds: Int = 0,
    val scenario: DemoScenario = DemoScenario.Weather,
    val audioOn: Boolean = false,
    val audioMenu: Boolean = false,
    val audioRoute: AudioRoute = AudioRoute.Phone,
    val muted: Boolean = false,
    val held: Boolean = false,
    val keypadOpen: Boolean = false,
    val manualDigits: String = "",
    val manualInputRevision: Long = 0,
    val selectedKey: String = "1",
) {
    fun reduce(action: Action): AppState = when (action) {
        is Action.Dial -> if (screen == Screen.Home) copy(number = action.number.take(32)) else this
        Action.DeleteDial -> if (screen == Screen.Home) copy(number = number.dropLast(1)) else this
        Action.ClearDial -> if (screen == Screen.Home) copy(number = "") else this
        is Action.Tab -> if (screen == Screen.Home || screen == Screen.Detail) copy(screen = Screen.Home, tab = action.tab) else this
        is Action.Detail -> if (screen == Screen.Home) copy(screen = Screen.Detail, number = action.number) else this
        is Action.Scenario -> if (screen == Screen.Home || screen == Screen.Detail) copy(scenario = action.scenario) else this
        Action.StartAgent -> if ((screen == Screen.Home || screen == Screen.Detail) && PhoneNumber(number).canUseAgent)
            AppState(screen = Screen.Purpose, tab = tab, number = number, agent = true, scenario = scenario) else this
        Action.StartNormal -> if ((screen == Screen.Home || screen == Screen.Detail) && PhoneNumber(number).canCall)
            AppState(screen = Screen.Call, tab = tab, number = number, phase = Phase.Listening, scenario = scenario) else this
        is Action.GoalChanged -> if (screen == Screen.Purpose) copy(goalDraft = action.text.take(160)) else this
        Action.CommitGoal -> if (screen == Screen.Purpose && goalDraft.isNotBlank())
            copy(screen = Screen.Call, phase = Phase.Dialing, goal = goalDraft.trim(), goalDraft = "",
                secureEntry = SecureEntry.Empty, manualDigits = "", audioOn = false, audioMenu = false, keypadOpen = false) else this
        is Action.AnswerChanged -> if (screen == Screen.Call && phase == Phase.Answer) copy(answerDraft = action.text.take(160)) else this
        Action.CommitAnswer -> if (screen == Screen.Call && phase == Phase.Answer && answerDraft.isNotBlank())
            copy(phase = Phase.Selecting, answerDraft = "") else this
        is Action.Select -> if (screen == Screen.Call && phase == Phase.Choice && action.key in listOf("1", "3", "5"))
            copy(phase = Phase.Selecting, selectedKey = action.key) else this
        is Action.Key -> when {
            screen != Screen.Call -> this
            phase == Phase.Secure -> {
                copy(secureEntry = secureEntry.append(action.digit))
            }
            keypadOpen && action.digit in "0123456789*#" -> copy(manualDigits = (manualDigits + action.digit).takeLast(16), manualInputRevision = manualInputRevision + 1)
            else -> this
        }
        Action.DeleteSecure -> if (screen == Screen.Call && phase == Phase.Secure) copy(secureEntry = secureEntry.delete()) else this
        Action.CommitSecure -> if (screen == Screen.Call && phase == Phase.Secure && secureEntry.count == 6)
            copy(secureEntry = SecureEntry.Empty, phase = Phase.Sending) else this
        Action.OpenKeypad -> if (screen == Screen.Call && phase != Phase.Secure && phase != Phase.Sending) copy(keypadOpen = !keypadOpen, manualDigits = "", audioMenu = false) else this
        Action.ToggleAudio -> if (screen == Screen.Call && agent && phase != Phase.Dialing) copy(audioOn = !audioOn) else this
        Action.ToggleAudioMenu -> if (screen == Screen.Call) copy(audioMenu = !audioMenu) else this
        is Action.Route -> if (screen == Screen.Call) copy(audioRoute = action.route, audioMenu = false) else this
        Action.ToggleMute -> if (screen == Screen.Call && !agent) copy(muted = !muted) else this
        Action.ToggleHold -> if (screen == Screen.Call && !agent) copy(held = !held) else this
        Action.Back -> when {
            audioMenu -> copy(audioMenu = false)
            screen == Screen.Call && keypadOpen -> copy(keypadOpen = false)
            screen == Screen.Call -> this
            screen == Screen.Detail -> copy(screen = Screen.Home)
            else -> copy(screen = Screen.Home, tab = PhoneTab.Keypad, goalDraft = "", answerDraft = "", secureEntry = SecureEntry.Empty, audioMenu = false)
        }
        Action.End -> if (screen == Screen.Call) copy(screen = Screen.Ended, secureEntry = SecureEntry.Empty,
            goalDraft = "", answerDraft = "", goal = "", manualDigits = "", audioMenu = false, keypadOpen = false, audioOn = false) else this
        Action.Tick -> if (screen == Screen.Call && phase != Phase.Dialing) copy(elapsedSeconds = elapsedSeconds + 1) else this
        Action.DemoAdvance -> if (screen != Screen.Call || !agent || keypadOpen) this else when (phase) {
            Phase.Dialing -> copy(phase = Phase.Listening)
            Phase.Listening -> copy(phase = when (scenario) {
                DemoScenario.Weather -> Phase.Selecting
                DemoScenario.Choice -> Phase.Choice
                DemoScenario.Answer -> Phase.Answer
                DemoScenario.Secure -> Phase.Secure
                DemoScenario.Handoff -> Phase.WaitingAgent
            })
            Phase.Selecting -> copy(phase = Phase.ListeningResult)
            Phase.ListeningResult -> copy(phase = Phase.Done)
            Phase.Sending -> copy(phase = Phase.Selecting)
            else -> this
        }
    }
}

fun formatDuration(seconds: Int): String = "%02d:%02d".format(seconds.coerceAtLeast(0) / 60, seconds.coerceAtLeast(0) % 60)

fun formatPhone(raw: String): String {
    val number = PhoneNumber(raw).digits
    if (number.any { it !in '0'..'9' }) return number
    val prefixLength = when {
        number.startsWith("02") && number.length <= 10 -> 2
        number.startsWith("0") && number.length <= 11 -> 3
        listOf("15", "16", "18").any(number::startsWith) && number.length <= 8 -> 4
        else -> return number
    }
    if (number.length <= prefixLength) return number
    val middleLength = when {
        prefixLength == 4 || number.startsWith("010") || number.startsWith("070") -> 4
        number.length > prefixLength + 7 -> 4
        else -> 3
    }
    return listOf(number.take(prefixLength), number.drop(prefixLength).take(middleLength),
        number.drop(prefixLength + middleLength)).filter { it.isNotEmpty() }.joinToString("-")
}
