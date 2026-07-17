package com.lakshay.healthcare.shared.util

// ssn stored as Long, leading zeros lost — pad back to 9 before slicing
fun maskSsnLast4(ssn: Long?): String =
    ssn?.toString()?.padStart(9, '0')?.takeLast(4)?.let { "***-**-$it" } ?: ""
