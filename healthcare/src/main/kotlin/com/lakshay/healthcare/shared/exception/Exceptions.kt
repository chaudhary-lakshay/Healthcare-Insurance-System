package com.lakshay.healthcare.shared.exception

class InvalidSsnException(message: String) : RuntimeException(message)

class ResourceNotFoundException(message: String) : RuntimeException(message)

class DuplicateResourceException(message: String) : RuntimeException(message)

class UnauthorizedException(message: String) : RuntimeException(message)

class ValidationException(message: String) : RuntimeException(message)
