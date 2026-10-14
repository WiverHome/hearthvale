package town

/** Дата партии дня: ГГГГММДД по UTC (без Calendar - в TeaVM его нет) */
fun todayDate(): Int = dateFromDays(Math.floorDiv(System.currentTimeMillis(), 86_400_000L))

/** Дни от 1970-01-01 -> ГГГГММДД (гражданский календарь, алгоритм Говарда Хиннанта) */
fun dateFromDays(days: Long): Int {
    val z = days + 719468
    val era = Math.floorDiv(z, 146097L)
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = if (mp < 10) mp + 3 else mp - 9
    val y = yoe + era * 400 + (if (m <= 2) 1 else 0)
    return (y * 10000 + m * 100 + d).toInt()
}

/** 20261012 -> «12.10.2026» */
fun dateText(date: Int): String {
    val d = date % 100
    val m = date / 100 % 100
    return (if (d < 10) "0$d" else "$d") + "." + (if (m < 10) "0$m" else "$m") + "." + (date / 10000)
}
