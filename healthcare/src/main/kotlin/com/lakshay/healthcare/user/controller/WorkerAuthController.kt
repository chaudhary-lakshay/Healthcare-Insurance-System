package com.lakshay.healthcare.user.controller

import com.lakshay.healthcare.admin.dto.WorkerDataResponse
import com.lakshay.healthcare.user.dto.ActivateRequest
import com.lakshay.healthcare.user.dto.LoginRequest
import com.lakshay.healthcare.user.dto.LoginResponse
import com.lakshay.healthcare.user.dto.RegisterRequest
import com.lakshay.healthcare.user.service.WorkerMgmtService
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
@RequestMapping("/worker-api")
class WorkerAuthController(
    private val workerService: WorkerMgmtService
) {

    @PostMapping("/save")
    fun registerWorker(@RequestBody request: RegisterRequest): ResponseEntity<Map<String, Any>> {
        val result = workerService.registerWorker(request)
        return ResponseEntity.ok(
            mapOf(
                "message" to "Worker registered successfully. Check email for temporary password.",
                "workerId" to result.id
            )
        )
    }

    @PostMapping("/activate")
    fun activateWorker(@RequestBody request: ActivateRequest): ResponseEntity<Map<String, String>> {
        val message = workerService.activateWorker(request)
        return ResponseEntity.ok(mapOf("message" to message))
    }

    @PostMapping("/login")
    fun loginWorker(@RequestBody request: LoginRequest): ResponseEntity<LoginResponse> {
        val response = workerService.loginWorker(request)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/report")
    fun listWorkers(): ResponseEntity<List<WorkerDataResponse>> {
        val workers = workerService.listWorkers()
        return ResponseEntity.ok(workers)
    }

    @GetMapping("/find/{id}")
    fun showWorkerById(@PathVariable id: Long): ResponseEntity<WorkerDataResponse> {
        val worker = workerService.showWorkerByWorkerId(id)
        return ResponseEntity.ok(worker)
    }

    @PutMapping("/update")
    fun updateWorker(@RequestBody request: WorkerDataResponse): ResponseEntity<Map<String, String>> {
        val message = workerService.updateWorker(request)
        return ResponseEntity.ok(mapOf("message" to message))
    }

    @DeleteMapping("/delete/{id}")
    fun deleteWorker(@PathVariable id: Long): ResponseEntity<Map<String, String>> {
        val message = workerService.deleteWorkerById(id)
        return ResponseEntity.ok(mapOf("message" to message))
    }

    @PatchMapping("/changeStatus/{id}/{status}")
    fun changeWorkerStatus(
        @PathVariable id: Long,
        @PathVariable status: String
    ): ResponseEntity<Map<String, String>> {
        val message = workerService.changeWorkerStatus(id, status)
        return ResponseEntity.ok(mapOf("message" to message))
    }
}
