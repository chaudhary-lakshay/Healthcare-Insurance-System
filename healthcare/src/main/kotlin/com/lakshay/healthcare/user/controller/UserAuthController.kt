package com.lakshay.healthcare.user.controller

import com.lakshay.healthcare.admin.dto.UserDataResponse
import com.lakshay.healthcare.user.dto.ActivateRequest
import com.lakshay.healthcare.user.dto.LoginRequest
import com.lakshay.healthcare.user.dto.LoginResponse
import com.lakshay.healthcare.user.dto.RegisterRequest
import com.lakshay.healthcare.user.service.UserMgmtService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/user-api")
class UserAuthController(
    private val userService: UserMgmtService
) {

    @PostMapping("/save")
    fun registerUser(@RequestBody request: RegisterRequest): ResponseEntity<Map<String, Any>> {
        val result = userService.registerUser(request)
        return ResponseEntity.ok(
            mapOf(
                "message" to "User registered successfully. Check email for temporary password.",
                "userId" to result.id
            )
        )
    }

    @PostMapping("/activate")
    fun activateUser(@RequestBody request: ActivateRequest): ResponseEntity<Map<String, String>> {
        val message = userService.activateUser(request)
        return ResponseEntity.ok(mapOf("message" to message))
    }

    @PostMapping("/login")
    fun loginUser(@RequestBody request: LoginRequest): ResponseEntity<LoginResponse> {
        val response = userService.loginUser(request)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/report")
    fun listUsers(): ResponseEntity<List<UserDataResponse>> {
        val users = userService.listUsers()
        return ResponseEntity.ok(users)
    }

    @GetMapping("/find/{id}")
    fun showUserById(@PathVariable id: Long): ResponseEntity<UserDataResponse> {
        val user = userService.showUserByUserId(id)
        return ResponseEntity.ok(user)
    }

    @PutMapping("/update")
    fun updateUser(@RequestBody request: UserDataResponse): ResponseEntity<Map<String, String>> {
        val message = userService.updateUser(request)
        return ResponseEntity.ok(mapOf("message" to message))
    }

    @DeleteMapping("/delete/{id}")
    fun deleteUser(@PathVariable id: Long): ResponseEntity<Map<String, String>> {
        val message = userService.deleteUserById(id)
        return ResponseEntity.ok(mapOf("message" to message))
    }

    @PatchMapping("/changeStatus/{id}/{status}")
    fun changeUserStatus(
        @PathVariable id: Long,
        @PathVariable status: String
    ): ResponseEntity<Map<String, String>> {
        val message = userService.changeUserStatus(id, status)
        return ResponseEntity.ok(mapOf("message" to message))
    }
}
