package com.lakshay.healthcare.shared.util

private const val SSN_LENGTH = 9
private const val MASK_VISIBLE_DIGITS = 4

fun maskSsnLast4(ssn: Long?): String =
    ssn?.toString()?.padStart(SSN_LENGTH, '0')?.takeLast(MASK_VISIBLE_DIGITS)?.let { "***-**-$it" } ?: ""
