package com.goldquest.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val DAY = DateTimeFormatter.ofPattern("dd/MM")
private val DAY_TIME = DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm")

fun formatDay(date: LocalDate): String = date.format(DAY)

fun formatDateTime(value: LocalDateTime): String = value.format(DAY_TIME)
