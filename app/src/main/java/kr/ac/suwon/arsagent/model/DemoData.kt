package kr.ac.suwon.arsagent.model

data class DemoContact(val name: String, val number: String, val note: String, val time: String)

val demoContacts = listOf(
    DemoContact("기상청 기상콜센터", "131", "Agent 이용", "16:20"),
    DemoContact("김민수", "01012345678", "부재중", "14:32"),
    DemoContact("테스트 ARS (예시)", "07000000000", "발신", "11:05"),
    DemoContact("박지연", "01023456789", "수신", "18:42"),
    DemoContact("우체국 고객센터", "15881300", "발신", "10:16"),
)

fun contactName(number: String): String = demoContacts.firstOrNull {
    it.number == PhoneNumber(number).digits
}?.name ?: "기관 고객센터"
