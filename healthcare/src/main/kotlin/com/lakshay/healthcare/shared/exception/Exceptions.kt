package com.lakshay.healthcare.shared.exception

class InvalidSsnException(message: String) : RuntimeException(message)

class ResourceNotFoundException(message: String) : RuntimeException(message)

class DuplicateResourceException(message: String) : RuntimeException(message)

class UnauthorizedException(message: String) : RuntimeException(message)

class ValidationException(message: String) : RuntimeException(message)

class ForbiddenException(message: String) : RuntimeException(message)

class AccountLockedException(
    val retryAfterSeconds: Long,
    message: String = "Too many failed attempts. Try again later."
) : RuntimeException(message)
